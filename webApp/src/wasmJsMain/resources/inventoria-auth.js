// Google sign-in for the web app, via Google Identity Services' OAuth token client. Resolves with
// a Google access token, which the Kotlin side exchanges for a Firebase session
// (FirebaseAuthRest.signInWithGoogleAccessToken). Kept in plain JS because it is only glue around
// the GIS script loaded in index.html.
window.inventoriaGoogleSignIn = function (clientId) {
    return new Promise(function (resolve, reject) {
        var gis = window.google && window.google.accounts && window.google.accounts.oauth2;
        if (!gis) {
            reject(new Error("Google sign-in is still loading or was blocked. Try again in a moment."));
            return;
        }
        var client = gis.initTokenClient({
            client_id: clientId,
            scope: "openid email profile",
            callback: function (response) {
                if (response.error) reject(new Error(response.error_description || response.error));
                else resolve(response.access_token);
            },
            error_callback: function (error) {
                reject(new Error(error.type === "popup_closed" ? "Sign-in was cancelled." : (error.message || "Sign-in failed.")));
            }
        });
        client.requestAccessToken();
    });
};

// Hides the plain-HTML loading note once Compose has drawn its first frame.
window.inventoriaHideBoot = function () {
    var boot = document.getElementById("boot");
    if (boot) boot.remove();
};

window.inventoriaPrefersDark = function () {
    return !!(window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);
};
