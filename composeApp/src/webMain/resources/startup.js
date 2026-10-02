(() => {
    const storageKey = "oms.startup";
    const texts = {
        uk: {
            preparing: "Підготовка системи…",
            assets: "Завантаження компонентів застосунку…",
            opening: "Відкриваємо робочий простір…",
            session: "Перевірка сеансу…",
            interface: "Запуск інтерфейсу…",
            ready: "Готово",
            elapsed: "Час завантаження"
        },
        en: {
            preparing: "Preparing the system…",
            assets: "Loading application components…",
            opening: "Opening your workspace…",
            session: "Checking your session…",
            interface: "Starting the interface…",
            ready: "Ready",
            elapsed: "Loading time"
        }
    };
    let locale = "uk";
    let active = false;
    let startedAt = 0;
    let progress = 0;
    let warmProgress = 0;
    let warmupPromise = null;
    let timer = null;

    const stored = (() => {
        try { return JSON.parse(sessionStorage.getItem(storageKey) || "{}"); }
        catch (_) { return {}; }
    })();
    if (stored.active) {
        active = true;
        startedAt = Number(stored.startedAt) || Date.now();
        progress = Number(stored.progress) || 0;
        locale = stored.locale === "en" ? "en" : "uk";
    }

    const text = key => texts[locale][key] || texts.uk[key] || key;
    const elapsedText = () => {
        const seconds = Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
        return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
    };
    const localizeShell = () => {
        const brand = document.querySelector(".oms-startup-brand");
        if (!brand) return;
        const english = locale === "en";
        brand.querySelector("h1").textContent = english ? "Online Monitoring System" : "Система онлайн-моніторингу.";
        brand.querySelector("p").textContent = english
            ? "A unified system for project tracking, inspections and financial control."
            : "Єдина система для відстеження проєктів, інспекцій і фінансового контролю.";
        const features = english
            ? ["Real-time project monitoring", "Inspection reports and work control", "Financial monitoring"]
            : ["Моніторинг проєктів у реальному часі", "Інспекційні звіти та контроль робіт", "Фінансовий моніторинг"];
        brand.querySelectorAll("li").forEach((item, index) => { item.textContent = features[index]; });
        const ghost = document.querySelector(".oms-startup-auth-ghost");
        if (ghost) {
            ghost.querySelector("h2").textContent = english ? "Welcome back" : "З поверненням";
            ghost.querySelector("p").textContent = english ? "Please sign in to your account." : "Будь ласка, увійдіть до свого облікового запису.";
            const labels = ghost.querySelectorAll(".oms-startup-label");
            if (labels[0]) labels[0].textContent = english ? "Username or email" : "Логін або електронна пошта";
            if (labels[1]) labels[1].textContent = english ? "Password" : "Пароль";
            const options = ghost.querySelectorAll(".oms-startup-options span");
            if (options[0]) options[0].textContent = english ? "□ Remember me" : "□ Запам’ятати мене";
            if (options[1]) options[1].textContent = english ? "Forgot password?" : "Забули пароль?";
            const buttons = ghost.querySelectorAll(".oms-startup-button");
            if (buttons[0]) buttons[0].textContent = english ? "Sign in" : "Увійти";
            if (buttons[1]) buttons[1].textContent = english ? "Continue as guest" : "Продовжити як гість";
        }
        const language = document.querySelector(".oms-startup-language");
        if (language) language.textContent = english ? "UA" : "EN";
    };
    const persist = () => {
        if (!active) return;
        sessionStorage.setItem(storageKey, JSON.stringify({ active, startedAt, progress, locale }));
    };
    const render = (messageKey) => {
        const container = document.getElementById("oms-startup-status");
        if (!container) return;
        localizeShell();
        container.hidden = !active;
        document.body.classList.toggle("oms-starting", active);
        const value = Math.max(0, Math.min(100, Math.round(progress)));
        const bar = document.getElementById("oms-startup-progress-bar");
        const percent = document.getElementById("oms-startup-percent");
        const message = document.getElementById("oms-startup-message");
        const elapsed = document.getElementById("oms-startup-timer");
        if (bar) {
            bar.style.width = `${value}%`;
            bar.parentElement?.setAttribute("aria-valuenow", String(value));
        }
        if (percent) percent.textContent = `${value}%`;
        if (message && messageKey) message.textContent = text(messageKey);
        if (elapsed) elapsed.textContent = `${text("elapsed")}: ${elapsedText()}`;
    };
    const ensureTimer = () => {
        if (timer) return;
        timer = setInterval(() => render(), 250);
    };
    const report = (value, messageKey) => {
        if (!active) return;
        progress = Math.max(progress, Number(value) || 0);
        persist();
        render(messageKey);
    };
    const reportWarmup = value => {
        warmProgress = Math.max(warmProgress, value);
        if (active) report(warmProgress, "assets");
    };

    async function readAsset(asset, totals) {
        const response = await fetch(new URL(asset.url, document.baseURI), { cache: "force-cache", credentials: "same-origin" });
        if (!response.ok) throw new Error(`Asset ${response.status}`);
        const reader = response.body?.getReader();
        if (!reader) { totals.loaded += asset.bytes; reportWarmup(8 + totals.loaded / totals.size * 80); return; }
        let loaded = 0;
        while (true) {
            const chunk = await reader.read();
            if (chunk.done) break;
            loaded += chunk.value.byteLength;
            const bounded = Math.min(asset.bytes, loaded);
            totals.loaded += bounded - (asset.reported || 0);
            asset.reported = bounded;
            reportWarmup(8 + totals.loaded / totals.size * 80);
        }
        totals.loaded += asset.bytes - (asset.reported || 0);
        asset.reported = asset.bytes;
        reportWarmup(8 + totals.loaded / totals.size * 80);
    }

    function warmApplicationAssets() {
        if (warmupPromise) return warmupPromise;
        warmupPromise = (async () => {
            const response = await fetch(new URL("assets-manifest.json", document.baseURI), { cache: "no-cache" });
            if (!response.ok) throw new Error(`Manifest ${response.status}`);
            const manifest = await response.json();
            const assets = (manifest.assets || []).filter(asset => asset.url && Number(asset.bytes) > 0);
            if (!assets.length) throw new Error("Empty asset manifest");
            const totals = { loaded: 0, size: assets.reduce((sum, asset) => sum + Number(asset.bytes), 0) };
            await Promise.all(assets.map(asset => readAsset({ ...asset, bytes: Number(asset.bytes), reported: 0 }, totals)));
            warmProgress = 88;
            if (active) report(88, "opening");
            return true;
        })().catch(() => {
            // Startup is still allowed to proceed: normal script loading can
            // recover from a stale/missing cache warmer.
            if (active) report(12, "opening");
            return false;
        });
        return warmupPromise;
    }

    window.omsWarmApplicationAssets = warmApplicationAssets;
    window.beginOmsStartup = language => {
        locale = language === "en" ? "en" : "uk";
        active = true;
        startedAt = Date.now();
        progress = Math.max(progress, warmProgress, 3);
        persist();
        ensureTimer();
        render("preparing");
        return warmApplicationAssets();
    };
    window.reportOmsStartupProgress = (value, messageKey) => {
        if (!active) {
            active = true;
            startedAt = Date.now();
        }
        ensureTimer();
        report(value, messageKey || "interface");
    };
    window.completeOmsStartup = () => {
        report(100, "ready");
        performance.mark("oms-dashboard-handoff");
        sessionStorage.removeItem(storageKey);
        setTimeout(() => {
            active = false;
            clearInterval(timer);
            timer = null;
            document.getElementById("oms-loading-overlay")?.classList.remove("visible");
            document.body.classList.remove("oms-starting");
        }, 220);
    };

    if (active) {
        ensureTimer();
        window.addEventListener("DOMContentLoaded", () => {
            report(Math.max(progress, 91), "session");
        }, { once: true });
    }
})();
