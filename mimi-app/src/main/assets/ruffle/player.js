(() => {
    "use strict";

    let playerApi = null;
    window.mimiRuffleLoaded = false;
    window.mimiRuffleState = "loading";
    window.mimiRuffleError = "";
    window.mimiRuffleShouldRun = false;
    window.mimiSetRuffleActive = (active) => {
        window.mimiRuffleShouldRun = Boolean(active);
        if (!playerApi) return;
        if (window.mimiRuffleShouldRun) {
            playerApi.resume();
        }
        else playerApi.suspend();
    };

    const showError = (error) => {
        window.mimiRuffleState = "error";
        window.mimiRuffleLoaded = false;
        window.mimiRuffleError = String(error && (error.message || error) || "Unknown player error").slice(0, 1000);
        // A WASM panic can also make suspend() throw. Still surface the original failure.
        try { if (playerApi) playerApi.suspend(); } catch (_) { /* already stopped */ }
        if (error) console.error("Unable to load Flash content", error);
        document.getElementById("player-error").hidden = false;
    };

    window.addEventListener("error", (event) => showError(event.error || event.message));
    window.addEventListener("unhandledrejection", (event) => showError(event.reason));

    try {
        // The Android version alone does not identify the independently updated WebView.
        // Keep unsupported syntax inside Function so old engines can show a useful error.
        try {
            new Function("return ({ value: 1 })?.value ?? 0")();
            if (typeof WebAssembly !== "object") throw new Error("WebAssembly unavailable");
        } catch (_) {
            showError(new Error("Update Android System WebView to play Flash content"));
            window.mimiRuffleState = "unsupported";
            return;
        }
        const ruffle = window.RufflePlayer.newest();
        const player = ruffle.createPlayer();
        player.id = "ruffle-player";
        document.getElementById("player-container").appendChild(player);
        playerApi = player.ruffle();
        player.addEventListener("loadeddata", () => {
            window.mimiRuffleLoaded = true;
            window.mimiRuffleState = "ready";
            window.mimiSetRuffleActive(window.mimiRuffleShouldRun);
        }, { once: true });
        playerApi.load({
            url: "/movie/current.swf",
            publicPath: "/ruffle/",
            // wgpu-webgl can initialize successfully and then panic compiling shaders
            // on SwiftShader/GPUs with only 256 vertex uniform vectors. The legacy
            // WebGL renderer avoids that fatal path and retains Canvas fallback.
            preferredRenderer: "webgl",
            autoplay: "off",
            splashScreen: false,
            unmuteOverlay: "visible",
            allowScriptAccess: false,
            allowNetworking: "internal",
            openUrlMode: "deny",
            allowFullscreen: false,
            backgroundExecutionMode: "none",
            contextMenu: "off",
            showSwfDownload: false,
            polyfills: false,
            credentialAllowList: [],
            socketProxy: []
        }).catch(showError);
    } catch (error) {
        showError(error);
    }
})();
