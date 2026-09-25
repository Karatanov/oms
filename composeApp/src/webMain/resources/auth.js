// First-party, tab-scoped credentials: Safari blocks the API's cross-site cookie.
// Never put bearer tokens into download URLs, localStorage or external requests.
(() => {
    const key = 'oms.accessToken';
    let token = '';
    try { token = sessionStorage.getItem(key) || ''; } catch (_) { /* Memory-only fallback. */ }
    window.setOmsAccessToken = value => {
        token = value || '';
        try { if (token) sessionStorage.setItem(key, token); else sessionStorage.removeItem(key); } catch (_) {}
    };
    const apiUrl = value => {
        const base = new URL(omsApiUrl(''), location.href);
        const url = new URL(value, location.href);
        return url.origin === base.origin && url.pathname.startsWith(base.pathname + '/');
    };
    window.omsAuthorizationFor = url => apiUrl(url) && token ? 'Bearer ' + token : '';
    window.omsAuthenticatedFetch = (url, options = {}) => {
        const headers = new Headers(options.headers);
        const authorization = omsAuthorizationFor(url);
        if (authorization) headers.set('Authorization', authorization);
        return fetch(url, { ...options, headers, credentials: 'include' });
    };
    window.clearOmsAuthentication = () => {
        setOmsAccessToken('');
        // The local credential is cleared even if the server is unavailable.
        fetch(omsApiUrl('/auth/logout'), { method: 'POST', credentials: 'include' }).catch(() => {});
    };
    window.omsSetImageSource = async (image, url) => {
        const generation = (image.omsGeneration || 0) + 1;
        image.omsGeneration = generation;
        if (!omsAuthorizationFor(url)) { image.src = url; return; }
        try {
            const response = await omsAuthenticatedFetch(url);
            if (!response.ok) throw new Error('Image unavailable');
            const blob = await response.blob();
            if (image.omsGeneration !== generation) return;
            const objectUrl = URL.createObjectURL(blob);
            image.addEventListener('load', () => URL.revokeObjectURL(objectUrl), { once: true });
            image.addEventListener('error', () => URL.revokeObjectURL(objectUrl), { once: true });
            image.src = objectUrl;
        } catch (_) {
            if (image.omsGeneration === generation) image.dispatchEvent(new Event('error'));
        }
    };
    window.openOmsDownload = async url => {
        if (!apiUrl(url)) return;
        try {
            const response = await omsAuthenticatedFetch(url);
            if (!response.ok) throw new Error('Download unavailable');
            const blobUrl = URL.createObjectURL(await response.blob());
            const disposition = response.headers.get('Content-Disposition') || '';
            const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
            const plain = /filename="?([^";]+)"?/i.exec(disposition);
            let filename = plain?.[1] || 'download';
            if (encoded) { try { filename = decodeURIComponent(encoded[1]); } catch (_) {} }
            const anchor = document.createElement('a');
            anchor.href = blobUrl; anchor.download = filename;
            document.body.append(anchor); anchor.click(); anchor.remove();
            setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
        } catch (_) {
            alert(omsLanguage === 'en' ? 'Could not download the file. Sign in again or retry.' : 'Не вдалося завантажити файл. Увійдіть повторно або повторіть спробу.');
        }
    };
})();
