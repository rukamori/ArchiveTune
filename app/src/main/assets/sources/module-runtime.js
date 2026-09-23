globalThis.console = Object.freeze({log() {}, info() {}, warn() {}, error() {}, debug() {}});
globalThis.atob = value => __sourceBase64Decode(String(value));
globalThis.btoa = value => __sourceBase64Encode(String(value));
class SourceHeaders {
    constructor(input = {}) {
        this.values = new Map();
        const entries = input instanceof SourceHeaders ? input.entries() : Array.isArray(input) ? input : Object.entries(input);
        for (const [name, value] of entries) this.set(name, value);
    }
    set(name, value) { this.values.set(String(name).toLowerCase(), String(value)); }
    get(name) { return this.values.get(String(name).toLowerCase()) ?? null; }
    has(name) { return this.values.has(String(name).toLowerCase()); }
    delete(name) { this.values.delete(String(name).toLowerCase()); }
    append(name, value) { const previous = this.get(name); this.set(name, previous ? previous + ', ' + value : value); }
    entries() { return this.values.entries(); }
    forEach(fn) { this.values.forEach((value, key) => fn(value, key, this)); }
    [Symbol.iterator]() { return this.entries(); }
}
globalThis.Headers = SourceHeaders;
class SourceSearchParams {
    constructor(input = '') {
        this.pairs = [];
        if (typeof input === 'string') {
            for (const pair of input.replace(/^\?/, '').split('&').filter(Boolean)) {
                const index = pair.indexOf('=');
                const decode = text => decodeURIComponent(text.replace(/\+/g, ' '));
                this.pairs.push([decode(index < 0 ? pair : pair.slice(0, index)), decode(index < 0 ? '' : pair.slice(index + 1))]);
            }
        } else for (const [key, value] of Array.isArray(input) ? input : Object.entries(input)) this.append(key, value);
    }
    append(key, value) { this.pairs.push([String(key), String(value)]); }
    get(key) { return this.pairs.find(pair => pair[0] === String(key))?.[1] ?? null; }
    getAll(key) { return this.pairs.filter(pair => pair[0] === String(key)).map(pair => pair[1]); }
    has(key) { return this.get(key) !== null; }
    delete(key) { this.pairs = this.pairs.filter(pair => pair[0] !== String(key)); }
    set(key, value) { this.delete(key); this.append(key, value); }
    toString() { return this.pairs.map(pair => pair.map(encodeURIComponent).join('=')).join('&'); }
    entries() { return this.pairs[Symbol.iterator](); }
    [Symbol.iterator]() { return this.entries(); }
}
globalThis.URLSearchParams = SourceSearchParams;
globalThis.URL = class {
    constructor(url, base) {
        Object.assign(this, JSON.parse(__sourceUrl(String(url), base)));
        this.origin = this.protocol + '//' + this.host;
        this.searchParams = new SourceSearchParams(this.search);
    }
    toString() { const query = this.searchParams.toString(); return this.origin + this.pathname + (query ? '?' + query : '') + this.hash; }
    toJSON() { return this.toString(); }
};
globalThis.AbortController = class {
    constructor() { this.signal = {aborted: false, reason: undefined}; }
    abort(reason) { this.signal.aborted = true; this.signal.reason = reason; }
};
let timerCounter = 0;
const timers = new Set();
globalThis.setTimeout = (fn, ms, ...args) => {
    const id = ++timerCounter;
    timers.add(id);
    __sourceDelay(Math.max(0, Math.min(Number(ms) || 0, 12000))).then(() => {
        if (timers.delete(id)) fn(...args);
    });
    return id;
};
globalThis.clearTimeout = id => timers.delete(id);
globalThis.fetch = async (input, options = {}) => {
    if (options.signal?.aborted) throw new Error('Aborted');
    const headers = new SourceHeaders(options.headers);
    const result = JSON.parse(await __sourceFetch(JSON.stringify({
        url: String(input), method: options.method || 'GET', headers: Object.fromEntries(headers),
        body: options.body == null ? '' : String(options.body)
    })));
    if (options.signal?.aborted) throw new Error('Aborted');
    const response = () => ({
        ok: result.status >= 200 && result.status < 300, status: result.status,
        headers: new SourceHeaders(result.headers),
        text: async () => result.body, json: async () => JSON.parse(result.body), clone: response
    });
    return response();
};
