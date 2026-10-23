"""Tìm phần tử UI theo text/content-desc trong bản dump uiautomator, in tọa độ tâm 'x y'."""
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
for node in ET.parse(path).iter("node"):
    label = (node.get("text") or "") + "|" + (node.get("content-desc") or "")
    if target in label:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
