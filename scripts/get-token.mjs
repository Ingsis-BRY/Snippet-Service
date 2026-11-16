import { spawn } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { createServer } from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const log = (...args) => console.error(...args);
const envFile = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", ".env");

function setting(name) {
    if (process.env[name]) return process.env[name];
    if (existsSync(envFile)) {
        for (const line of readFileSync(envFile, "utf8").split(/\r?\n/)) {
            const match = line.match(/^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$/);
            if (match && match[1] === name) return match[2];
        }
    }
    log(`Missing ${name}: set it in the environment or in ${envFile}`);
    process.exit(1);
}

const domain = setting("AUTH0_DOMAIN");
const audience = setting("AUTH0_AUDIENCE");
const clientId = setting("AUTH0_CLI_CLIENT_ID");
const redirectUri = "http://localhost:8485/callback";
const scope = process.argv[2] ?? "read:snippets write:snippets";

const verifier = randomBytes(32).toString("base64url");
const challenge = createHash("sha256").update(verifier).digest("base64url");
const state = randomBytes(16).toString("base64url");

const authorizeUrl = new URL(`https://${domain}/authorize`);
authorizeUrl.search = new URLSearchParams({
    response_type: "code",
    client_id: clientId,
    redirect_uri: redirectUri,
    audience,
    scope,
    code_challenge: challenge,
    code_challenge_method: "S256",
    state,
    prompt: "login",
}).toString();

const server = createServer(async (req, res) => {
    const url = new URL(req.url, redirectUri);
    if (url.pathname !== "/callback") {
        res.writeHead(404).end();
        return;
    }
    const finish = (status, message) => {
        res.writeHead(status, { "Content-Type": "text/plain; charset=utf-8" })
            .end(message, () => server.closeAllConnections());
        server.close();
    };

    if (url.searchParams.get("error")) {
        finish(400, "Login failed. Check the terminal.");
        log(`Login failed: ${url.searchParams.get("error")}: ${url.searchParams.get("error_description")}`);
        process.exitCode = 1;
        return;
    }
    if (url.searchParams.get("state") !== state) {
        finish(400, "Unexpected state. Check the terminal.");
        log("The callback's state doesn't match: ignoring it.");
        process.exitCode = 1;
        return;
    }

    const response = await fetch(`https://${domain}/oauth/token`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            grant_type: "authorization_code",
            client_id: clientId,
            code: url.searchParams.get("code"),
            code_verifier: verifier,
            redirect_uri: redirectUri,
        }),
    }).catch((err) => ({ ok: false, status: 0, json: async () => ({ error: err.message }) }));
    const body = await response.json();
    if (!response.ok) {
        finish(500, "Could not get the token. Check the terminal.");
        log(`Token request failed (${response.status}):`, body);
        process.exitCode = 1;
        return;
    }

    finish(200, "Logged in. You can close this tab and go back to the terminal.");
    const claims = JSON.parse(Buffer.from(body.access_token.split(".")[1], "base64url").toString());
    log(`Token for ${claims.sub}, scopes: "${claims.scope ?? ""}", expires in ${body.expires_in}s`);
    console.log(body.access_token);
});

server.listen(8485, "localhost", () => {
    log("Opening the Auth0 login page. If it doesn't open, visit:");
    log(authorizeUrl.toString());
    const opener =
        process.platform === "win32" ? ["rundll32", ["url.dll,FileProtocolHandler", authorizeUrl.toString()]]
            : process.platform === "darwin" ? ["open", [authorizeUrl.toString()]]
                : ["xdg-open", [authorizeUrl.toString()]];
    spawn(opener[0], opener[1], { stdio: "ignore", detached: true }).unref();
});
