"""Append the current operational amendment without rewriting historical specifications."""
from pathlib import Path
from docx import Document
from docx.shared import RGBColor

TITLE = "Financial monitoring and filter lifecycle November 2026"
SECTIONS = [
    ("Currency and session lifecycle", "All monetary charts initially use UAH. EUR remains available where supported, using existing source conversions and recorded rates. Changing the language does not change numeric amounts. A browser reload, new sign-in, restored browser session or user switch starts with default filters and UAH. Within the current session, table search, filters, sorting and page size survive navigation. Language, theme, credentials and other non-filter preferences are not cleared."),
    ("Global financial scope", "The searchable Subproject selector at the top of Financial Monitoring accepts a project code or name. It uses the same authoritative subproject filter as Dashboard drill-down. Clear the selected project or use the page-level Reset filters action to return to all permitted projects. KPI totals, transaction charts, investment grouping and both financial tables follow this scope. Backend role and project-access checks are unchanged."),
    ("Regional investment structure", "Financing structure by oblast replaces Planned financing and actual payments by oblast and Financial progress by oblast. It shows three adjacent, not stacked, UAH bars: Total Investment Cost, Investment Cost excluding local co-financing, and Local co-financing. Values come from the imported payment-monitoring investment fields, not cash payments or completion percentages. Budgets retain their snapshot grain: a selected transaction month or purpose does not invent a monthly budget allocation."),
    ("Financial aggregation", "Investment values are aggregated by the canonical oblast of each subproject. A filled parent budget is preferred to its component lot budgets independently for each indicator; when a parent value is absent, available child values contribute. Decimal minor units are used for sums. Missing data are shown as unavailable, not zero. Any source difference between total investment and EIB investment plus local co-financing is retained and identified in the tooltip, not repaired with fabricated values."),
    ("Payment register and refresh", "The lower, dated transaction table is named Payment register (Реєстр платежів); the cumulative subproject monitoring table keeps its existing name. The single Update data action is located in the Financial Monitoring page heading, subject to the existing update permission and disabled while an import is running. The existing confirmation, source download/upload, progress and cancellation workflow is retained. Manual source refresh preserves the current page and table filters; browser reload resets them."),
    ("Reset controls and semantic colors", "Table-level Reset filters clears only that table's local search and filters and returns to its first page. It does not remove page-level subproject, month or project-access restrictions. Financial tables share their local code search. Reset actions are also available for subprojects, procurement, inspection reports, documents, user administration and audit activity. Contract/payment categories share semantic colors: Works Information, Technical supervision Purple, Engineer consultant Success, Equipment Warning. Construction and sector indicators use the corresponding registry chip mappings; colors do not depend on sort order."),
]

def update(path: Path):
    doc = Document(path)
    if any(p.text == TITLE for p in doc.paragraphs):
        in_amendment = False
        remove_next = False
        for p in doc.paragraphs:
            in_amendment = in_amendment or p.text == TITLE
            if in_amendment and (remove_next or p.text == "Delivery and verification"):
                remove_next = p.text == "Delivery and verification"
                p._element.getparent().remove(p._element)
                continue
            if in_amendment and p.style.name.startswith("Heading"):
                for run in p.runs:
                    run.font.color.rgb = RGBColor(0, 0, 0)
            if in_amendment and "Within the current session, table search" in p.text:
                p.text = p.text.replace("Within the current session, table search, filters, sorting and page size survive navigation.",
                    "Within the current session, Subprojects, Procurement and financial table preferences survive navigation.")
        doc.save(path)
        print(f"Verified amendment formatting {path}")
        return
    # Keep every existing paragraph, table, image and pending editorial change.
    before = len(doc.paragraphs)
    doc.add_page_break()
    paragraph = doc.add_heading(TITLE, level=1)
    for run in paragraph.runs:
        run.font.color.rgb = RGBColor(0, 0, 0)
    doc.add_paragraph("This amendment defines the current financial workspace behavior and supersedes earlier descriptions of its currencies, regional charts and filter persistence. Other historical baseline sections retain their original scope.")
    for heading, text in SECTIONS:
        paragraph = doc.add_heading(heading, level=2)
        for run in paragraph.runs:
            run.font.color.rgb = RGBColor(0, 0, 0)
        doc.add_paragraph(text)
    doc.save(path)
    check = Document(path)
    assert len(check.paragraphs) > before and any(p.text == TITLE for p in check.paragraphs)
    print(f"Updated and reopened {path}")

if __name__ == "__main__":
    for name in ["OMS_Supplementary_Specification.docx", "OMS_User_Manual.docx", "OMS_Administrator_Guide.docx", "OMS_Technical_Handover.docx"]:
        update(Path("documentation") / name)
