(() => {
    "use strict";

    let playerApi = null;
    window.mimiRuffleLoaded = false;
    window.mimiRuffleState = "loading";
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
        if (playerApi) playerApi.suspend();
        if (error) console.error("Unable to load Flash content", error);
        document.getElementById("player-error").hidden = false;
    };

    try {
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
