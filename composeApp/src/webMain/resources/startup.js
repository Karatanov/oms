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
            elapsed: "Час завантаження",
            failed: "Не вдалося запустити інтерфейс. Перевірте з’єднання та повторіть спробу.",
            retry: "Повторити"
        },
        en: {
            preparing: "Preparing the system…",
            assets: "Loading application components…",
            opening: "Opening your workspace…",
            session: "Checking your session…",
            interface: "Starting the interface…",
            ready: "Ready",
            elapsed: "Loading time",
            failed: "The interface could not be started. Check your connection and try again.",
            retry: "Retry"
        }
    };
    let locale = "uk";
    let active = false;
    let startedAt = 0;
    let progress = 0;
    let warmProgress = 0;
    let warmupPromise = null;
    let timer = null;
    let watchdog = null;
    let failed = false;

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
        const retry = document.getElementById("oms-startup-retry");
        if (retry) {
            retry.hidden = !failed;
            retry.textContent = text("retry");
        }
    };
    const ensureTimer = () => {
        if (timer) return;
        timer = setInterval(() => render(), 250);
    };
    const report = (value, messageKey) => {
        if (!active || failed) return;
        progress = Math.max(progress, Number(value) || 0);
        persist();
        render(messageKey);
    };
    const clearWatchdog = () => {
        if (watchdog) clearTimeout(watchdog);
        watchdog = null;
    };
    const failStartup = cause => {
        if (!active || failed) return;
        failed = true;
        clearWatchdog();
        clearInterval(timer);
        timer = null;
        sessionStorage.removeItem(storageKey);
        // Keep the failure visible instead of leaving a progress indicator
        // indefinitely at 93%. The technical cause stays in DevTools only.
        console.error("OMS startup failed", cause);
        render("failed");
    };
    const ensureWatchdog = () => {
        if (watchdog || failed) return;
        // The production renderer is large and may need tens of seconds on a
        // cold connection. This is a failure screen, not an auto-dismissal:
        // a user must explicitly retry if mounting never completes.
        watchdog = setTimeout(() => failStartup(new Error("Startup did not complete within 90 seconds")), 90_000);
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
    window.beginOmsStartup = (language, warmAssets = true) => {
        locale = language === "en" ? "en" : "uk";
        active = true;
        failed = false;
        startedAt = Date.now();
        progress = Math.max(progress, warmProgress, 3);
        persist();
        ensureTimer();
        ensureWatchdog();
        render("preparing");
        // A navigation destroys in-flight fetches from the login document.
        // Preserve only the visible startup state there; let the app document
        // own its asset transfer so a slow connection never downloads the
        // same incomplete bundle twice.
        return warmAssets ? warmApplicationAssets() : Promise.resolve(true);
    };
    window.reportOmsStartupProgress = (value, messageKey) => {
        if (!active) {
            active = true;
            startedAt = Date.now();
        }
        ensureTimer();
        ensureWatchdog();
        report(value, messageKey || "interface");
    };
    window.completeOmsStartup = () => {
        if (failed) return;
        report(100, "ready");
        clearWatchdog();
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

    window.retryOmsStartup = () => window.location.reload();
    window.addEventListener("error", event => {
        if (!active) return;
        const target = event.target;
        const resourceFailure = target && (target.tagName === "SCRIPT" || target.tagName === "LINK");
        failStartup(resourceFailure ? new Error(`Could not load ${target.src || target.href || "startup resource"}`) : event.error || new Error(event.message || "Unhandled startup error"));
    }, true);
    window.addEventListener("unhandledrejection", event => {
        if (active) failStartup(event.reason || new Error("Unhandled startup rejection"));
    });

    if (active) {
        ensureTimer();
        ensureWatchdog();
        // The document declares native preloads for the renderer and bundle.
        // Do not duplicate them with fetch(): preload and fetch have different
        // request modes and some browsers download both on a slow connection.
        // The progress overlay is retained through the hand-off and Compose
        // reports the final readiness stages below.
        window.addEventListener("DOMContentLoaded", () => {
            report(Math.max(progress, 91), "session");
        }, { once: true });
    }
})();
