from pathlib import Path
import re,json
from PIL import Image
P=Path(__file__).resolve().parents[1]
D=P/'shared/src/commonMain/kotlin/ovh/gabrielhuav/pow/domain/models/streetfighter'
def make_atlas():
    catalog=(D/'SfArcadeLadder.kt').read_text(encoding='utf-8').split('val ALL_PARTICIPANTS = listOf(')[1].split('\n    )')[0]
    ids=re.findall(r'SfFighterId\.([A-Z_0-9]+)',catalog)
    models=(D/'SfModels.kt').read_text(encoding='utf-8')
    records={m[0]:m[1:] for m in re.findall(r'\b([A-Z_0-9]+)\(\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"',models)}
    atlas=Image.new('RGBA',(1280,((len(ids)+9)//10)*128))
    for i,id in enumerate(ids):
        _,_,data,sheet=records[id]
        x,y,w,h=json.loads((P/'app/src/main/assets'/data).read_text(encoding='utf-8'))['frames']['idle-1']['src']
        im=Image.open(P/'app/src/main/assets'/sheet).convert('RGBA').crop((x,y,x+w,y+h))
        box=im.getchannel('A').point(lambda a:255 if a>100 else 0).getbbox()
        assert box,id
        im=im.crop(box)
        scale=min(116/im.width,116/im.height)
        im=im.resize((round(im.width*scale),round(im.height*scale)),Image.Resampling.NEAREST)
        atlas.alpha_composite(im,((i%10)*128+(128-im.width)//2,(i//10)*128+124-im.height))
    target=P/'app/src/main/assets/STREETFIGHTER/IMAGES/team_roster.webp'
    atlas.save(target,lossless=True)
    (P/'app/src/main/assets/STREETFIGHTER/DATA/team_roster.json').write_text(json.dumps(ids,indent=2),encoding='utf-8')
    print(f'Atlas: {len(ids)} sprites, {target.stat().st_size} bytes')
make_atlas()
