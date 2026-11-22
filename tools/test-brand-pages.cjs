const { chromium } = require("../build/brand-tests/node_modules/playwright");
const assert = require("node:assert/strict");

async function main() {
    const browser = await chromium.launch();
    try {
        for (const [port, brand] of [[18181, "UR III"], [18182, "USIF"]]) {
            for (const width of [390, 768, 1440]) {
                const page = await browser.newPage({ viewport: { width, height: 900 } });
                const origin = "http://127.0.0.1:" + port;
                const external = [];
                page.on("request", req => {
                    if (req.url().startsWith("http") && new URL(req.url()).origin !== origin) external.push(req.url());
                });
                await page.goto(origin, { waitUntil: "domcontentloaded" });
                assert.ok((await page.title()).includes(brand));
                const uk = await page.locator(".brand h1").textContent();
                if (brand === "USIF") assert.equal(uk, "Система онлайн-моніторингу проєктів");
                await page.locator("#language").click();
                if (brand === "USIF") assert.equal(await page.locator(".brand h1").textContent(), "Online Project Monitoring System");
                assert.equal(await page.locator("#submit").textContent(), "Sign in");
                await page.locator("#language").click();
                assert.equal(await page.locator(".brand h1").textContent(), uk);
                assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
                assert.deepEqual(external, []);
                await page.close();
            }
        }
    } finally {
        await browser.close();
    }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
