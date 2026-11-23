"""Materialize only the selected organization's public pages before starting Ktor."""
import json
import os
import re
import shutil
import sys
import tempfile
from html import escape
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent


def render_page(source, config, origin, theme):
    name = escape(config["name"])
    title = name + " OMS"
    uk, en = (escape(config["heading_" + lang]) for lang in ("uk", "en"))
    desc, english_desc = (escape(config["description_" + lang]) for lang in ("uk", "en"))
    page = source.replace('<html lang="uk">', f'<html lang="uk" data-oms-name="{name}">')
    page = page.replace("https://ua-oms.com", origin)
    page = re.sub(r'<title>.*?</title>', f'<title>{title} — {uk}</title>', page)
    for attr, value in {
        "description": desc, "theme-color": "#102c48", "og:site_name": title,
        "og:title": f"{title} — {en}", "twitter:title": f"{title} — {en}",
        "og:description": english_desc, "twitter:description": english_desc,
        "og:image:alt": title,
    }.items():
        page = re.sub(fr'((?:name|property)="{re.escape(attr)}" content=")[^"]*"', rf'\g<1>{value}"', page)
    schema = {"@context": "https://schema.org", "@type": "WebApplication", "name": title,
              "applicationCategory": "BusinessApplication", "operatingSystem": "Web",
              "url": origin + "/", "description": config["description_en"]}
    page = re.sub(r'(<script type="application/ld\+json">).*?(</script>)',
                  lambda m: m[1] + json.dumps(schema) + m[2], page, flags=re.S)
    page = page.replace("<strong>UR III</strong><small>TA to the implementation<br>of UR III Programme</small>",
                        f"<strong>{title}</strong>")
    page = page.replace("<strong>UR III OMS</strong>", f"<strong>{title}</strong>")
    page = re.sub(r'<h1>(?:OMS — )?Система онлайн-моніторингу\.?</h1>',
                  f'<h1 data-en="{en}">{uk}</h1>', page)
    page = page.replace("<p>Єдина система для відстеження проєктів, інспекцій і фінансового контролю.</p>",
                        f'<p data-en="{english_desc}">{desc}</p>')
    return page.replace("</head>", "<style>\n" + theme + "\n</style>\n</head>")


def prepare(brand, origin, source, target):
    if not re.fullmatch(r"[a-z][a-z0-9-]*", brand):
        raise ValueError("Invalid OMS_BRAND")
    parsed = urlsplit(origin)
    if not re.fullmatch(r"https://[a-z0-9.-]+(?::[0-9]+)?", origin) or not parsed.hostname:
        raise ValueError("Expected an HTTPS public origin")
    profile = ROOT / brand
    config = json.loads((profile / "profile.json").read_text(encoding="utf-8"))
    theme = (profile / "theme.css").read_text(encoding="utf-8")
    shutil.copytree(source, target, dirs_exist_ok=True)
    for file in ("favicon.svg", "social-preview.png", "privacy.html", "terms.html"):
        shutil.copyfile(profile / file, target / file)
    for file in ("index.html", "app.html", "login.html"):
        path = target / file
        page = render_page(path.read_text(encoding="utf-8"), config, origin, theme)
        if "UR III" in page or "ua-oms.com" in page or 'data-oms-name=' not in page:
            raise ValueError(f"Unexpected public branding in {file}")
        path.write_text(page, encoding="utf-8")
    for file in ("robots.txt", "sitemap.xml"):
        path = target / file
        path.write_text(path.read_text(encoding="utf-8").replace("https://ua-oms.com", origin), encoding="utf-8")


if __name__ == "__main__":
    brand = os.environ.get("OMS_BRAND", "ur3")
    if brand != "ur3":
        target = Path(tempfile.mkdtemp(prefix="oms-public-"))
        prepare(brand, os.environ["OMS_PUBLIC_BASE_URL"].rstrip("/"),
                Path(os.environ.get("OMS_WEB_DIR", "/opt/oms/web")), target)
        os.environ["OMS_WEB_DIR"] = str(target)
    os.execvp(sys.argv[1], sys.argv[1:])
