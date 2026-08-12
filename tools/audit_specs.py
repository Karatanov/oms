from docx import Document
from pathlib import Path

FILES = [
    Path(r"D:\2026\OMS\OMS Functional Specification.docx"),
    Path(r"D:\2026\OMS\OMS_Supplementary_Specification.docx"),
    Path(r"D:\2026\OMS\UMITAF-OMS-July26.docx"),
]

for path in FILES:
    print(f"\n### {path.name}")
    document = Document(path)
    for index, paragraph in enumerate(document.paragraphs):
        if paragraph.text.strip() and paragraph.style.name.startswith("Heading"):
            print(f"{index}: [{paragraph.style.name}] {paragraph.text}")
