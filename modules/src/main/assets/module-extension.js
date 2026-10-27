globalThis.__moduleCreateExtension = function(metadata, load) {
  const native = input => JSON.parse(__moduleExtensionCall(JSON.stringify(input)));
  native({action:'configure', metadata});
  let transfer = null, manifests = [], resolutions = [], initialized = null, registered = null;
  const prepared = new Map();
  const stopped = new Error('Transfer delegated to the host player');
  function active() { if (transfer) throw stopped; }
  function remember(body, base) {
    function append(text, detail = {}) {
      if (typeof text !== 'string' || text.length > 1024 * 1024 || !(/<MPD[\s>]/i.test(text) || /^\s*#EXTM3U/.test(text))) return;
      manifests.push({text, base, detail});
      if (manifests.length > 16) manifests.shift();
    }
    append(body);
    let parsed;
    try { parsed = JSON.parse(body); } catch (_) { return; }
    const pending = [[parsed, 0]];
    let visited = 0;
    while (pending.length && visited++ < 2048) {
      const [value, depth] = pending.pop();
      if (!value || typeof value !== 'object' || depth > 8) continue;
      if (typeof value.manifest === 'string' && value.manifest.length < 1400000) {
        try {
          const text=new TextDecoder().decode(Uint8Array.from(atob(value.manifest), c=>c.charCodeAt(0)));
          append(text, value);
          if (/^\s*\{/.test(text)) {
            const decoded=JSON.parse(text);
            if (Array.isArray(decoded.urls)) {
              resolutions.push({urls:decoded.urls.map(url=>new URL(String(url),base).href),detail:value,mimeType:decoded.mimeType,codecs:decoded.codecs});
              if (resolutions.length>16) resolutions.shift();
            }
          }
        } catch (_) {}
      }
      Object.values(value).forEach(child => { if (child && typeof child === 'object') pending.push([child, depth+1]); });
    }
  }
  function request(method, url, body, headers) {
    active();
    const options=headers || {};
    if (options.params) {
      const target=new URL(String(url));
      Object.entries(options.params).forEach(([key,value])=>target.searchParams.set(key,value));
      url=target.href;
    }
    headers=Object.fromEntries(new Headers(options.headers || options));
    if (options.params) delete headers.params;
    if (body != null && typeof body !== 'string') body=JSON.stringify(body);
    if (body != null && !Object.keys(headers).some(key=>key.toLowerCase()==='content-type')) headers['Content-Type']='application/json';
    const response = native({action:'request', method, url:String(url), body:body ?? null, headers});
    remember(response.body, response.url);
    return response;
  }
  const http = Object.freeze({
    get:(url,headers)=>request('GET',url,null,headers), head:(url,headers)=>request('HEAD',url,null,headers),
    post:(url,body,headers)=>request('POST',url,body,headers), put:(url,body,headers)=>request('PUT',url,body,headers),
    patch:(url,body,headers)=>request('PATCH',url,body,headers), delete:(url,headers)=>request('DELETE',url,null,headers),
    request:(url,options={})=>request(options.method || 'GET',url,options.body,options.headers),
    clearCookies:()=>native({action:'clearCookies'})
  });
  function fetch(url, options={}) {
    const response=http.request(String(url),options);
    return Object.freeze({ok:response.ok,status:response.status,statusText:response.statusText,url:response.url,headers:new Headers(response.headers),
      text:()=>response.body,json:()=>JSON.parse(response.body),arrayBuffer:()=>Uint8Array.from(atob(response.base64),c=>c.charCodeAt(0)).buffer});
  }
  globalThis.fetch=fetch;
  const storage = Object.freeze({
    get:key=>{active();return native({action:'storageGet',key:String(key)});},
    set:(key,value)=>{active();return native({action:'storageSet',key:String(key),value});},
    remove:key=>{active();return native({action:'storageRemove',key:String(key)});},
    clear:()=>{active();return native({action:'storageClear'});},
    keys:()=>{active();return native({action:'storageKeys'});}
  });
  const utils = Object.freeze({
    appVersion:()=>native({action:'version'}), appUserAgent:()=>native({action:'userAgent'}), randomUserAgent:()=>native({action:'userAgent'}),
    uuid:()=>native({action:'uuid'}), sha256:value=>native({action:'digest',algorithm:'sha256',value:String(value)}),
    sha1:value=>native({action:'digest',algorithm:'sha1',value:String(value)}), md5:value=>native({action:'digest',algorithm:'md5',value:String(value)}),
    base64Encode:value=>btoa(Array.from(new TextEncoder().encode(String(value)),byte=>String.fromCharCode(byte)).join('')),
    base64Decode:value=>new TextDecoder().decode(Uint8Array.from(atob(String(value)),c=>c.charCodeAt(0))),
    sleep:millis=>{active();return native({action:'sleep',millis:Number(millis)});},
    getResolutionRemainingMs:()=>native({action:'remaining'}), isDownloadCancelled:()=>native({action:'cancelled'}),
    parseJSON:value=>JSON.parse(value), stringifyJSON:value=>JSON.stringify(value)
  });
  const session = Object.freeze({
    signedFetch(method,path,body,headers) {
      active();
      const response = native({action:'sessionFetch',method:String(method),path:String(path),
        body:body == null ? '' : typeof body === 'string' ? body : JSON.stringify(body),headers:headers || {}});
      remember(response.body || '', response.url || metadata.signedSession?.baseUrl || '');
      return response;
    },
    status:()=>native({action:'sessionStatus'}), clear:()=>native({action:'sessionClear'})
  });
  function capture(urls, path, options, segmented) {
    active();
    if (metadata.permissions?.file!==true) throw new Error('Extension transfer permission is required');
    if (!Array.isArray(urls) || !urls.length || urls.length > 10000) throw new TypeError('Invalid transfer URLs');
    const targets = urls.map(value=>new URL(String(value)).href);
    const headers = options?.headers || {};
    let detail={};
    if (!segmented) {
      const resolution=resolutions.slice().reverse().find(value=>value.urls.includes(targets[0]));
      detail=resolution?.detail || {};
      transfer = {streamUrl:targets[0],headers,mimeType:resolution?.mimeType || '',codecs:resolution?.codecs || '',sampleRate:detail.sampleRate};
    }
    else {
      const first = targets[0];
      const manifest = manifests.slice().reverse().find(item => {
        const init = item.text.match(/initialization\s*=\s*["']([^"']+)["']/i);
        if (init) return new URL(init[1].replace(/&amp;/g,'&'),item.base).href === first;
        if (/^\s*#EXTM3U/.test(item.text)) return item.text.split(/\r?\n/).some(line=>line.trim() && !line.startsWith('#') && new URL(line.trim(),item.base).href === first);
        return false;
      });
      if (!manifest) throw new Error('Segmented playback requires the original DASH or HLS manifest');
      detail=manifest.detail;
      transfer = {streamUrl:manifest.base,inlineManifest:manifest.text,headers,
        mimeType:/<MPD[\s>]/i.test(manifest.text)?'application/dash+xml':'application/x-mpegURL',
        codecs:manifest.text.match(/codecs\s*=\s*["']([^"']+)["']/i)?.[1] || '',
        sampleRate:manifest.detail.sampleRate || Number(manifest.text.match(/audioSamplingRate\s*=\s*["'](\d+)["']/i)?.[1]) || undefined};
    }
    transfer.outputExtension = String(path || '').split('.').pop();
    transfer.variant=JSON.stringify([detail.audioQuality ?? detail.audio_quality ?? null,detail.bitDepth ?? detail.bit_depth ?? null,
      transfer.sampleRate ?? null,transfer.codecs || '',transfer.outputExtension]);
    transfer.allowedDomains = metadata.permissions?.network || [];
    throw stopped;
  }
  const file = Object.freeze({
    download:(url,path,options)=>capture([url],path,options,false),
    downloadSegments:(urls,path,options)=>capture(urls,path,options,true),
    exists:path=>{
      active();
      const value=String(path);
      if (!value.startsWith('/resolved/') || value.includes('/../') || value.includes('\\')) throw new Error('Filesystem access is unavailable during stream resolution');
      return false;
    },
    delete:()=>{active();throw new Error('Filesystem access is unavailable during stream resolution');}
  });
  const normalize = value=>String(value || '').normalize('NFKD').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
  const matching = Object.freeze({
    compareStrings(first,second) {
      const a=normalize(first),b=normalize(second);
      if (a===b) return a?1:0;
      if (!a || !b) return 0;
      const tokens=new Set(a.split(' ')), other=new Set(b.split(' '));
      return 2*[...tokens].filter(token=>other.has(token)).length/(tokens.size+other.size);
    },
    compareDuration:(a,b)=>Math.max(0,1-Math.abs(Number(a)-Number(b))/Math.max(Number(a),Number(b),1))
  });
  load({http,storage,utils,session,file,fetch,matching,log:console,gobackend:Object.freeze({}),ffmpeg:Object.freeze({}),
    registerExtension(value) {
      if (registered || !value || typeof value !== 'object') throw new TypeError('Invalid extension registration');
      registered=value;
    }});
  if (!registered || typeof registered.searchTracks!=='function' || typeof registered.download!=='function') throw new TypeError('A playback extension must register searchTracks and download');
  async function start(context) {
    transfer=null;manifests=[];resolutions=[];native({action:'begin'});
    const settings={};
    Object.keys(metadata.settings || {}).forEach(key=>{
      const configured=context?.settings?.[key], field=metadata.settings[key];
      const value=configured && Object.prototype.hasOwnProperty.call(configured,'value') ? configured.value :
        Object.prototype.hasOwnProperty.call(field,'defaultValue') ? field.defaultValue : field.value ?? null;
      Object.defineProperty(settings,key,{value,enumerable:true,writable:true,configurable:true});
    });
    const identity=JSON.stringify(settings);
    if (initialized!==identity) {
      if (typeof registered.initialize==='function' && await registered.initialize(settings)===false) throw new Error('Extension initialization failed');
      initialized=identity;
    }
  }
  function track(value) {
    return {...value,id:value.id ?? value.track_id,name:value.name,title:value.title || value.name,
      artists:value.artists ?? value.artist,album:value.album_name || value.album,durationMs:value.duration_ms ?? value.durationMs};
  }
  const provider={...metadata,authentication:!!metadata.signedSession,
    async searchTracks(query,limit,context) {
      await start(context);
      const result=await registered.searchTracks(query,limit);
      const values=Array.isArray(result)?result:result?.tracks;
      if (!Array.isArray(values)) throw new TypeError('Invalid extension search result');
      prepared.clear();
      const candidates=values.filter(value=>value && typeof value==='object').slice(0,100);
      candidates.forEach(value=>{if(value.prepared_context)prepared.set(String(value.id ?? value.track_id),value.prepared_context);});
      return candidates.map(track);
    },
    async getTrackStreamUrl(id,quality,context) {
      await start(context);
      let outcome;
      try {
        outcome=await registered.download(id,quality,'/resolved/audio',()=>{}, {duration_ms:(context?.track?.duration || 0)*1000,preparedContext:prepared.get(String(id))});
      } catch(error) { if (!transfer) throw error; }
      if (!transfer) {
        const error=new Error('Extension did not resolve a playable transfer');
        if (outcome?.error_type==='timeout') error.code='RESOLUTION_TIMEOUT';
        else if (outcome?.error_type==='verification_required') error.code='VERIFY_REQUIRED';
        throw error;
      }
      const result={...transfer,provider:metadata.name,variant:JSON.stringify([quality ?? null,transfer.variant]),track:{id}};
      if (!result.inlineManifest && !result.mimeType) {
        const extension=result.outputExtension;
        if (extension==='flac') result.mimeType='audio/flac';
        else if (extension==='m4a' || extension==='mp4') result.mimeType='audio/mp4';
        else if (extension==='mp3') result.mimeType='audio/mpeg';
        else if (extension==='ogg' || extension==='opus') result.mimeType='audio/ogg';
      }
      transfer=null;
      return result;
    }
  };
  ['getAlbum','getArtist','getPlaylist'].forEach(key=>{if(typeof registered[key]==='function')provider[key]=registered[key].bind(registered);});
  if (metadata.declaredCapabilities) {
    const known=['searchTracks','getTrackStreamUrl','getAlbum','getArtist','getPlaylist'];
    const actual=known.filter(key=>typeof provider[key]==='function');
    if (actual.length!==metadata.declaredCapabilities.length || actual.some(key=>!metadata.declaredCapabilities.includes(key))) throw new TypeError('Extension capability mismatch');
  }
  return provider;
};
