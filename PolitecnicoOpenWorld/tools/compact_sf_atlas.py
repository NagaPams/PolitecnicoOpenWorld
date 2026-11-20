"""Compacta un atlas sin reescalar ni alterar las coordenadas de combate."""
from pathlib import Path
import json
from PIL import Image

def compact_character(assets, key, title):
    assets=Path(assets)
    path=assets/'IMAGES'/f'{title}.webp'
    data_path=assets/'DATA'/f'{key}.json'
    image=Image.open(path).convert('RGBA')
    data=json.loads(data_path.read_text(encoding='utf-8'))
    items=[]
    for name,frame in data['frames'].items():
        x,y,w,h=frame['src']
        cell=image.crop((x,y,x+w,y+h))
        box=cell.getbbox()
        if not box: raise ValueError(f'Frame vacio: {name}')
        box=(box[0],min(box[1],frame['origin'][1]-2),box[2],box[3])
        crop=cell.crop(box)
        width=(crop.width+5)//2*2
        height=(crop.height+5)//2*2
        items.append((name,crop,box,width,height))
    # Orígenes y rectángulos pares: el submuestreo x2 conserva límites exactos.
    packed={};x=y=height=0;limit=2048
    for name,crop,box,w,h in sorted(items,key=lambda i:i[4],reverse=True):
        if x+w>limit:x=0;y+=height;height=0
        packed[name]=(x,y,w,h,crop,box)
        x+=w;height=max(height,h)
    atlas=Image.new('RGBA',(limit,y+height))
    for name,(x,y,w,h,crop,box) in packed.items():
        atlas.paste(crop,(x+2,y+2))
        frame=data['frames'][name]
        old_origin=frame['origin']
        frame['src']=[x,y,w,h]
        frame['origin']=[old_origin[0]-box[0]+2,old_origin[1]-box[1]+2]
        # Verificar todos los píxeles y el anclaje antes de escribir.
        assert atlas.crop((x+2,y+2,x+2+crop.width,y+2+crop.height)).tobytes()==crop.tobytes()
        assert frame['origin'][0]+box[0]-2==old_origin[0]
        assert frame['origin'][1]+box[1]-2==old_origin[1]
        assert frame['origin'][1]>0
    atlas.save(path,'WEBP',lossless=True,method=6)
    data_path.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    result={'before':image.size,'after':atlas.size,'ram_before_mib':image.width*image.height*4/1048576,'ram_after_mib':atlas.width*atlas.height*4/1048576,'frames_verified':len(items)}
    print(json.dumps(result))
    return result

if __name__=='__main__':
    import sys
    compact_character(Path(__file__).resolve().parents[1]/'app/src/main/assets/STREETFIGHTER',sys.argv[1],sys.argv[2])
