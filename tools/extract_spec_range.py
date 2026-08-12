import sys

from docx import Document

document = Document(r"D:\2026\OMS\OMS_Supplementary_Specification.docx")
start, end = map(int, sys.argv[1:])
for index, paragraph in enumerate(document.paragraphs[start:end], start):
    if paragraph.text.strip():
        print(f"{index}: {paragraph.text}")
