(() => {
    "use strict";

    let playerApi = null;
    window.mimiRuffleLoaded = false;
    window.mimiRuffleShouldRun = false;
    window.mimiSetRuffleActive = (active) => {
        window.mimiRuffleShouldRun = Boolean(active);
        if (!playerApi) return;
        if (window.mimiRuffleShouldRun) playerApi.resume();
        else playerApi.suspend();
    };

    const showError = () => {
        document.getElementById("player-error").hidden = false;
    };

    try {
        const ruffle = window.RufflePlayer.newest();
        const player = ruffle.createPlayer();
        player.id = "ruffle-player";
        document.getElementById("player-container").appendChild(player);
        playerApi = player.ruffle();
        playerApi.load({
            url: "/movie/current.swf",
            publicPath: "/ruffle/",
            autoplay: "off",
            unmuteOverlay: "visible",
            allowScriptAccess: false,
            allowNetworking: "internal",
            openUrlMode: "deny",
            allowFullscreen: false,
            backgroundExecutionMode: "none",
            contextMenu: false,
            showSwfDownload: false,
            polyfills: false,
            credentialAllowList: [],
            socketProxy: []
        }).then(() => {
            window.mimiRuffleLoaded = true;
            window.mimiSetRuffleActive(window.mimiRuffleShouldRun);
        }).catch(showError);
    } catch (error) {
        showError();
    }
})();
