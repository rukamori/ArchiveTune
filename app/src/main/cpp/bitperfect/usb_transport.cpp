/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

#include <jni.h>
#include <libusb.h>
#include <sys/resource.h>
#include <sys/time.h>

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <future>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include "usb_stream.h"

using namespace usbaudio;

namespace {
void require(bool condition, const char *message) {
    if (!condition) throw std::runtime_error(message);
}

void check(int code) {
    if (code < 0) throw std::runtime_error(std::string("USB error ") + libusb_error_name(code));
}

struct Endpoint {
    uint8_t address = 0;
    uint8_t attributes = 0;
    size_t maxPacket = 0;
    int interval = 0;
    bool rateControl = false;
};

struct Candidate {
    int control = 0;
    int interface = 0;
    int alternate = 0;
    bool uac2 = false;
    int clock = 0;
    Endpoint data;
    bool hasFeedback = false;
    Endpoint feedback;
    Format format;
};

constexpr int kCandidateInts = 17;

struct ControlRequest {
    bool input;
    uint8_t request;
    uint16_t value;
    uint16_t index;
    std::vector<uint8_t> data;
    std::chrono::steady_clock::time_point deadline;
    std::promise<std::vector<uint8_t>> reply;
};

struct State {
    std::mutex mutex;
    std::condition_variable changed;
    std::deque<uint8_t> bytes;
    bool playing = false;
    bool closed = false;
    uint64_t frames = 0;
    uint64_t flush = 0;
    uint64_t flushed = 0;
    std::string error;
    std::deque<std::unique_ptr<ControlRequest>> controls;
};

struct Slot {
    libusb_transfer *transfer = nullptr;
    std::vector<uint8_t> bytes;
    bool pending = false;
    bool submitted = false;
    uint64_t musicFrames = 0;

    Slot(int packets, size_t maxBytes) : bytes(static_cast<size_t>(packets) * maxBytes) {
        transfer = libusb_alloc_transfer(packets);
        require(transfer != nullptr, "USB transfer allocation failed");
    }
    ~Slot() {
        if (transfer) libusb_free_transfer(transfer);
    }

    static void LIBUSB_CALL completed(libusb_transfer *transfer) {
        static_cast<Slot *>(transfer->user_data)->pending = false;
    }

    void submit(libusb_device_handle *handle, uint8_t address, const size_t *lengths, int count) {
        size_t total = 0;
        for (int i = 0; i < count; ++i) total += lengths[i];
        libusb_fill_iso_transfer(transfer, handle, address, bytes.data(), static_cast<int>(total), count,
                                 completed, this, 500);
        for (int i = 0; i < count; ++i) transfer->iso_packet_desc[i].length = static_cast<unsigned>(lengths[i]);
        check(libusb_submit_transfer(transfer));
        pending = true;
        submitted = true;
    }

    bool success() const {
        if (transfer->status != LIBUSB_TRANSFER_COMPLETED) return false;
        for (int i = 0; i < transfer->num_iso_packets; ++i) {
            const auto &packet = transfer->iso_packet_desc[i];
            if (packet.status != LIBUSB_TRANSFER_COMPLETED) return false;
            if ((transfer->endpoint & 0x80) == 0 && packet.actual_length != packet.length) return false;
        }
        return true;
    }

    size_t actual() const { return transfer->iso_packet_desc[0].actual_length; }
};

class Session {
public:
    libusb_context *context = nullptr;
    libusb_device_handle *handle = nullptr;
    std::vector<int> claimed;
    Candidate chosen;
    int chosenIndex = -1;
    bool highSpeed = false;
    uint32_t interval = 0;
    std::vector<std::unique_ptr<Slot>> slots;
    std::unique_ptr<Slot> feedbackSlot;

    Session(int fd, const std::vector<Candidate> &candidates) {
        libusb_init_option option{};
        option.option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY;
        check(libusb_init_context(&context, &option, 1));
        check(libusb_wrap_sys_device(context, static_cast<intptr_t>(fd), &handle));
        highSpeed = libusb_get_device_speed(libusb_get_device(handle)) >= LIBUSB_SPEED_HIGH;
        check(libusb_set_auto_detach_kernel_driver(handle, 1));
        std::string failure = "No supported USB endpoint";
        for (size_t i = 0; i < candidates.size(); ++i) {
            try {
                configure(candidates[i]);
            } catch (const std::exception &e) {
                failure = e.what();
                release();
                continue;
            }
            chosenIndex = static_cast<int>(i);
            const int packets = packetsPerTransfer();
            for (int s = 0; s < 4; ++s) slots.push_back(std::make_unique<Slot>(packets, chosen.data.maxPacket));
            if (chosen.hasFeedback) feedbackSlot = std::make_unique<Slot>(1, chosen.feedback.maxPacket);
            return;
        }
        throw std::runtime_error(failure);
    }

    ~Session() {
        drain();
        slots.clear();
        feedbackSlot.reset();
        release();
        if (handle) libusb_close(handle);
        if (context) libusb_exit(context);
    }

    int packetsPerTransfer() const {
        return static_cast<int>(std::min<uint32_t>(std::max<uint32_t>(4000 / interval, 1), 32));
    }

    void control(bool input, uint8_t request, uint16_t value, uint16_t index, uint8_t *data, uint16_t size,
                 bool endpoint) {
        const uint8_t kind = (input ? 0x80 : 0) | 0x20 | (endpoint ? 2 : 1);
        const int n = libusb_control_transfer(handle, kind, request, value, index, data, size, 500);
        check(n);
        require(n == size, "Short USB control response");
    }

    void configure(const Candidate &c) {
        const auto &ep = c.data;
        require(ep.interval >= 1 && ep.interval <= 4 && ep.maxPacket > 0 && ep.maxPacket <= 3072,
                "Unsupported USB interval/packet size");
        const uint32_t period = (highSpeed ? 125u : 1000u) * (1u << (ep.interval - 1));
        const uint64_t maximumFrames = (static_cast<uint64_t>(c.format.sampleRate) * period + 999999) / 1000000;
        require(maximumFrames * c.format.channels * c.format.subslot <= ep.maxPacket,
                "USB endpoint bandwidth too small");
        for (int interface : {c.control, c.interface}) {
            if (std::find(claimed.begin(), claimed.end(), interface) != claimed.end()) continue;
            check(libusb_claim_interface(handle, interface));
            claimed.push_back(interface);
        }
        chosen = c;
        check(libusb_set_interface_alt_setting(handle, c.interface, 0));
        if (c.uac2) {
            const uint16_t index = static_cast<uint16_t>((c.clock << 8) | c.control);
            uint8_t rate[4];
            std::memcpy(rate, &c.format.sampleRate, 4);
            uint8_t current[4] = {};
            control(true, 1, 0x100, index, current, 4, false);
            if (std::memcmp(current, rate, 4) != 0) control(false, 1, 0x100, index, rate, 4, false);
            control(true, 1, 0x100, index, current, 4, false);
            require(std::memcmp(current, rate, 4) == 0, "USB clock rate could not be verified");
        }
        check(libusb_set_interface_alt_setting(handle, c.interface, c.alternate));
        if (!c.uac2 && ep.rateControl) {
            uint8_t rate[3] = {static_cast<uint8_t>(c.format.sampleRate), static_cast<uint8_t>(c.format.sampleRate >> 8),
                               static_cast<uint8_t>(c.format.sampleRate >> 16)};
            uint8_t current[3] = {};
            control(false, 1, 0x100, ep.address, rate, 3, true);
            control(true, 0x81, 0x100, ep.address, current, 3, true);
            require(std::memcmp(current, rate, 3) == 0, "USB clock rate could not be verified");
        }
        interval = period;
    }

    void events() {
        timeval timeout{0, 1000};
        check(libusb_handle_events_timeout(context, &timeout));
    }

    void drain() {
        auto any = [this] {
            for (auto &slot : slots) if (slot->pending) return true;
            return feedbackSlot && feedbackSlot->pending;
        };
        for (auto &slot : slots) if (slot->pending) libusb_cancel_transfer(slot->transfer);
        if (feedbackSlot && feedbackSlot->pending) libusb_cancel_transfer(feedbackSlot->transfer);
        while (any()) {
            timeval timeout{0, 1000};
            libusb_handle_events_timeout(context, &timeout);
        }
    }

    void release() {
        if (!handle) return;
        if (chosen.alternate > 0) libusb_set_interface_alt_setting(handle, chosen.interface, 0);
        for (auto it = claimed.rbegin(); it != claimed.rend(); ++it) libusb_release_interface(handle, *it);
        claimed.clear();
        chosen = Candidate{};
    }

    void serve(ControlRequest &request) {
        if (std::chrono::steady_clock::now() > request.deadline) {
            request.reply.set_exception(std::make_exception_ptr(std::runtime_error("USB control request expired")));
            return;
        }
        try {
            control(request.input, request.request, request.value, request.index, request.data.data(),
                    static_cast<uint16_t>(request.data.size()), false);
            request.reply.set_value(std::move(request.data));
        } catch (...) {
            request.reply.set_exception(std::current_exception());
        }
    }

    void run(State &state) {
        const size_t frame = static_cast<size_t>(chosen.format.subslot) * chosen.format.channels;
        const int packets = packetsPerTransfer();
        PacketClock clock(chosen.format.sampleRate, interval);
        uint8_t marker = 0x05;
        auto lastFeedback = std::chrono::steady_clock::now();
        bool started = false;
        for (;;) {
            events();
            std::unique_lock<std::mutex> lock(state.mutex);
            if (state.closed) break;
            if (!state.controls.empty()) {
                auto request = std::move(state.controls.front());
                state.controls.pop_front();
                lock.unlock();
                serve(*request);
                lock.lock();
            }
            if (state.flush != state.flushed) {
                lock.unlock();
                drain();
                lock.lock();
                state.bytes.clear();
                state.frames = 0;
                state.flushed = state.flush;
                for (auto &slot : slots) {
                    slot->musicFrames = 0;
                    slot->submitted = false;
                }
                clock = PacketClock(chosen.format.sampleRate, interval);
                marker = 0x05;
                started = false;
                state.changed.notify_all();
            }
            for (auto &slot : slots) {
                if (slot->pending || !slot->submitted) continue;
                require(slot->success(), "USB audio transfer failed");
                state.frames += slot->musicFrames;
                slot->musicFrames = 0;
                slot->submitted = false;
            }
            if (!state.playing) {
                started = false;
                state.changed.wait_for(lock, std::chrono::milliseconds(20));
                continue;
            }
            if (!started) {
                lastFeedback = std::chrono::steady_clock::now();
                started = true;
            }
            if (feedbackSlot) {
                if (!feedbackSlot->pending) {
                    const size_t got = std::min(feedbackSlot->actual(), feedbackSlot->bytes.size());
                    if (feedbackSlot->submitted && got > 0 && feedbackSlot->success() &&
                        clock.feedback(feedbackSlot->bytes.data(), got, highSpeed, interval)) {
                        lastFeedback = std::chrono::steady_clock::now();
                    }
                    const size_t length = chosen.feedback.maxPacket;
                    feedbackSlot->submit(handle, chosen.feedback.address, &length, 1);
                }
                require(std::chrono::steady_clock::now() - lastFeedback < std::chrono::seconds(2),
                        "USB feedback stopped or invalid");
            }
            for (auto &slot : slots) {
                if (slot->pending || slot->submitted) continue;
                size_t lengths[32];
                size_t total = 0;
                for (int i = 0; i < packets; ++i) {
                    lengths[i] = clock.next() * frame;
                    require(lengths[i] <= chosen.data.maxPacket, "USB feedback exceeded endpoint capacity");
                    total += lengths[i];
                }
                const size_t available = std::min(total, state.bytes.size()) / frame * frame;
                std::copy(state.bytes.begin(), state.bytes.begin() + static_cast<long>(available), slot->bytes.begin());
                state.bytes.erase(state.bytes.begin(), state.bytes.begin() + static_cast<long>(available));
                finishTransfer(slot->bytes.data(), total, available, chosen.format, marker);
                slot->musicFrames = available / frame;
                slot->submit(handle, chosen.data.address, lengths, packets);
            }
            state.changed.notify_all();
        }
        drain();
    }
};

class Output {
    State state;
    std::thread worker;
    size_t frameBytes = 0;
    size_t capacity = 0;

    void throwIfFailed() {
        require(!state.closed, "USB output closed");
        if (!state.error.empty()) throw std::runtime_error(state.error);
    }

public:
    Format format;
    int chosen = -1;

    Output(int fd, std::vector<Candidate> candidates) {
        require(fd >= 0 && !candidates.empty() && candidates.size() <= 32, "Invalid USB device/configuration");
        std::promise<std::pair<int, Format>> opened;
        auto result = opened.get_future();
        worker = std::thread([this, fd, candidates = std::move(candidates), opened = std::move(opened)]() mutable {
            setpriority(PRIO_PROCESS, 0, -16);
            std::unique_ptr<Session> session;
            try {
                session = std::make_unique<Session>(fd, candidates);
                opened.set_value({session->chosenIndex, session->chosen.format});
            } catch (...) {
                opened.set_exception(std::current_exception());
                return;
            }
            try {
                session->run(state);
            } catch (const std::exception &e) {
                std::lock_guard<std::mutex> lock(state.mutex);
                state.error = e.what();
                state.playing = false;
            }
            session.reset();
            std::lock_guard<std::mutex> lock(state.mutex);
            for (auto &request : state.controls) {
                request->reply.set_exception(std::make_exception_ptr(std::runtime_error("USB output closed")));
            }
            state.controls.clear();
            state.changed.notify_all();
        });
        try {
            auto [index, chosenFormat] = result.get();
            chosen = index;
            format = chosenFormat;
        } catch (...) {
            worker.join();
            throw;
        }
        frameBytes = static_cast<size_t>(format.subslot) * format.channels;
        capacity = std::max<size_t>(static_cast<size_t>(format.sampleRate) * frameBytes / 5, 256 * 1024);
    }

    ~Output() { close(); }

    int write(const uint8_t *data, size_t size) {
        require(size <= 256 * 1024 && size % frameBytes == 0, "Invalid USB frame buffer");
        std::lock_guard<std::mutex> lock(state.mutex);
        throwIfFailed();
        const size_t n = std::min(size, capacity - std::min(capacity, state.bytes.size())) / frameBytes * frameBytes;
        state.bytes.insert(state.bytes.end(), data, data + n);
        state.changed.notify_all();
        return static_cast<int>(n);
    }

    size_t capacityFrames() const { return capacity / frameBytes; }

    void setPlaying(bool playing) {
        std::lock_guard<std::mutex> lock(state.mutex);
        throwIfFailed();
        state.playing = playing;
        state.changed.notify_all();
    }

    void flush() {
        std::unique_lock<std::mutex> lock(state.mutex);
        state.playing = false;
        state.flush++;
        state.changed.notify_all();
        const bool done = state.changed.wait_for(lock, std::chrono::seconds(2), [this] {
            return state.flushed == state.flush || !state.error.empty() || state.closed;
        });
        throwIfFailed();
        require(done, "USB cancellation timed out");
    }

    uint64_t frames() {
        std::lock_guard<std::mutex> lock(state.mutex);
        if (!state.error.empty()) throw std::runtime_error(state.error);
        return state.frames;
    }

    std::vector<uint8_t> control(bool input, int request, int value, int index, std::vector<uint8_t> data) {
        auto pending = std::make_unique<ControlRequest>();
        pending->input = input;
        pending->request = static_cast<uint8_t>(request);
        pending->value = static_cast<uint16_t>(value);
        pending->index = static_cast<uint16_t>(index);
        pending->data = std::move(data);
        pending->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(2);
        auto reply = pending->reply.get_future();
        {
            std::lock_guard<std::mutex> lock(state.mutex);
            throwIfFailed();
            require(state.controls.size() < 8, "USB control requests busy");
            state.controls.push_back(std::move(pending));
            state.changed.notify_all();
        }
        require(reply.wait_for(std::chrono::seconds(4)) == std::future_status::ready, "USB control request timed out");
        return reply.get();
    }

    void close() {
        {
            std::lock_guard<std::mutex> lock(state.mutex);
            state.closed = true;
            state.changed.notify_all();
        }
        if (worker.joinable()) worker.join();
    }
};

void report(JNIEnv *env, const std::exception &error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}

std::mutex handlesMutex;
std::unordered_map<jlong, std::shared_ptr<Output>> handles;
jlong nextHandle = 1;

std::shared_ptr<Output> get(jlong id) {
    std::lock_guard<std::mutex> lock(handlesMutex);
    auto it = handles.find(id);
    require(it != handles.end(), "USB output handle closed");
    return it->second;
}

std::vector<Candidate> decode(const jint *v, jsize length) {
    require(length > 0 && length % kCandidateInts == 0, "Invalid USB candidate list");
    std::vector<Candidate> result;
    for (jsize at = 0; at < length; at += kCandidateInts) {
        const jint *c = v + at;
        Candidate candidate;
        candidate.control = c[0];
        candidate.interface = c[1];
        candidate.alternate = c[2];
        candidate.uac2 = c[3] != 0;
        candidate.clock = c[4];
        candidate.data.address = static_cast<uint8_t>(c[5]);
        candidate.data.attributes = static_cast<uint8_t>(c[6]);
        candidate.data.maxPacket = static_cast<size_t>(c[7]);
        candidate.data.interval = c[8];
        candidate.data.rateControl = c[9] != 0;
        candidate.hasFeedback = c[10] >= 0;
        candidate.feedback.address = static_cast<uint8_t>(c[10]);
        candidate.feedback.maxPacket = static_cast<size_t>(c[11]);
        candidate.format.sampleRate = static_cast<uint32_t>(c[12]);
        candidate.format.channels = c[13];
        candidate.format.subslot = c[14];
        candidate.format.bits = c[15];
        require(c[16] >= 0 && c[16] <= 3, "Invalid USB encoding");
        candidate.format.encoding = static_cast<Encoding>(c[16]);
        require(candidate.format.channels >= 1 && candidate.format.channels <= 2 && candidate.format.subslot >= 2 &&
                    candidate.format.subslot <= 4 && candidate.format.sampleRate > 0,
                "Invalid USB format");
        result.push_back(candidate);
    }
    return result;
}
}

#define JNI_USB(name) Java_moe_rukamori_archivetune_playback_bitperfect_usb_UsbDirectNative_##name

extern "C" {
JNIEXPORT jlong JNICALL JNI_USB(open)(JNIEnv *env, jobject, jint fd, jintArray candidates) {
    try {
        const jsize length = env->GetArrayLength(candidates);
        std::vector<jint> values(static_cast<size_t>(length));
        env->GetIntArrayRegion(candidates, 0, length, values.data());
        auto output = std::make_shared<Output>(fd, decode(values.data(), length));
        std::lock_guard<std::mutex> lock(handlesMutex);
        const jlong id = nextHandle++;
        handles.emplace(id, std::move(output));
        return id;
    } catch (const std::exception &e) {
        report(env, e);
        return 0;
    }
}
JNIEXPORT jint JNICALL JNI_USB(chosen)(JNIEnv *env, jobject, jlong handle) {
    try { return get(handle)->chosen; } catch (const std::exception &e) { report(env, e); return -1; }
}
JNIEXPORT jint JNICALL JNI_USB(capacity)(JNIEnv *env, jobject, jlong handle) {
    try { return static_cast<jint>(get(handle)->capacityFrames()); } catch (const std::exception &e) { report(env, e); return 0; }
}
JNIEXPORT jint JNICALL JNI_USB(write)(JNIEnv *env, jobject, jlong handle, jobject buffer, jint offset, jint count) {
    try {
        auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(buffer));
        const auto size = env->GetDirectBufferCapacity(buffer);
        require(data && offset >= 0 && count >= 0 && offset <= size && count <= size - offset, "Invalid PCM buffer");
        return get(handle)->write(data + offset, static_cast<size_t>(count));
    } catch (const std::exception &e) {
        report(env, e);
        return 0;
    }
}
JNIEXPORT void JNICALL JNI_USB(setPlaying)(JNIEnv *env, jobject, jlong handle, jboolean playing) {
    try { get(handle)->setPlaying(playing); } catch (const std::exception &e) { report(env, e); }
}
JNIEXPORT void JNICALL JNI_USB(flush)(JNIEnv *env, jobject, jlong handle) {
    try { get(handle)->flush(); } catch (const std::exception &e) { report(env, e); }
}
JNIEXPORT jlong JNICALL JNI_USB(frames)(JNIEnv *env, jobject, jlong handle) {
    try { return static_cast<jlong>(get(handle)->frames()); } catch (const std::exception &e) { report(env, e); return 0; }
}
JNIEXPORT jbyteArray JNICALL JNI_USB(control)(JNIEnv *env, jobject, jlong handle, jboolean input, jint request,
                                              jint value, jint index, jbyteArray data) {
    try {
        const jsize length = env->GetArrayLength(data);
        require(length >= 0 && length <= 1024, "Invalid USB control length");
        std::vector<uint8_t> bytes(static_cast<size_t>(length));
        env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(bytes.data()));
        auto reply = get(handle)->control(input, request, value, index, std::move(bytes));
        auto result = env->NewByteArray(static_cast<jsize>(reply.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(reply.size()),
                                            reinterpret_cast<const jbyte *>(reply.data()));
        return result;
    } catch (const std::exception &e) {
        report(env, e);
        return nullptr;
    }
}
JNIEXPORT void JNICALL JNI_USB(close)(JNIEnv *, jobject, jlong handle) {
    std::shared_ptr<Output> output;
    {
        std::lock_guard<std::mutex> lock(handlesMutex);
        auto it = handles.find(handle);
        if (it == handles.end()) return;
        output = std::move(it->second);
        handles.erase(it);
    }
    output->close();
}
}
