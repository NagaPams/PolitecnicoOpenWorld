"""Run the English resource-key and format-placeholder QA checks."""
from pathlib import Path
import collections,json,re,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[2]
resources=root/'PolitecnicoOpenWorld/shared/src/commonMain/composeResources'
def read(folder):
    elements=ET.parse(resources/folder/'strings.xml').getroot().findall('string')
    counts=collections.Counter(e.get('name') for e in elements)
    return {e.get('name'):''.join(e.itertext()) for e in elements},sorted(k for k,n in counts.items() if n>1)
base,base_duplicates=read('values');english,en_duplicates=read('values-en')
tokens=lambda value:sorted(re.findall(r'%(?:\d+\$)?[sdfoxegbc]',value,re.I))
result={'base_string_count':len(base),'english_string_count':len(english),'missing_english_keys':sorted(base.keys()-english.keys()),'duplicate_base_keys':base_duplicates,'duplicate_english_keys':en_duplicates,'placeholder_mismatches':sorted(k for k in base.keys()&english.keys() if tokens(base[k])!=tokens(english[k]))}
(Path(__file__).parent/'localization-audit.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
raise SystemExit(1 if any(result[k] for k in ['missing_english_keys','duplicate_base_keys','duplicate_english_keys','placeholder_mismatches']) else 0)
