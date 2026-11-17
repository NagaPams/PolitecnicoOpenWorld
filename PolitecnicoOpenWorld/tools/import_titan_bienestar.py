from pathlib import Path
import json, shutil, re
import numpy as np
from PIL import Image, ImageDraw
from collections import deque

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / 'PolitecnicoOpenWorld'
ASSETS = PROJECT / 'app/src/main/assets/STREETFIGHTER'
SOURCES = ROOT / 'newSFAssets/TitanBienestar'
TEMP = Path(__import__('tempfile').gettempdir())
FILES = ['4dc596e9-5744-418b-90e8-ea8da5acf375', 'c4d9262d-84c0-4784-9828-08d9dbd03ce8', '1c386668-e521-4084-968d-1d2b17cfa53f', 'd9dbfd25-1f5a-45a2-959b-c92705b0e41d', 'b7a079a1-821c-43ba-b252-ca339fe5939a']

def cleaned(path):
    a = np.array(Image.open(path).convert('RGBA'))
    # Las hojas entregadas contienen residuos rojos del recorte y alfa casi invisible.
    red = (a[:,:,0] > 180) & (a[:,:,1] < 65) & (a[:,:,2] < 65)
    a[:,:,3][red | (a[:,:,3] < 24)] = 0
    return Image.fromarray(a)

def row(sheet, y0, y1, n, x0=0, x1=1):
    im = sheets[sheet]
    region = im.crop((round(x0*im.width), round(y0/1448*im.height), round(x1*im.width), round(y1/1448*im.height)))
    mask = np.array(region)[:,:,3] > 32
    occupied=np.flatnonzero(mask.any(axis=0))
    if not len(occupied): raise ValueError(('empty row',sheet,y0,y1))
    region=region.crop((int(occupied[0]),0,int(occupied[-1])+1,region.height))
    mask=np.array(region)[:,:,3]>32
    profile = mask.sum(axis=0)
    cuts = [0]
    for i in range(1,n):
        center = round(region.width*i/n)
        radius = max(2,round(region.width/n*.23))
        lo,hi = max(cuts[-1]+1,center-radius), min(region.width,center+radius)
        candidates = np.flatnonzero(profile[lo:hi] == profile[lo:hi].min()) + lo
        cuts.append(int(candidates[np.argmin(abs(candidates-center))]))
    cuts.append(region.width)
    result=[]
    for a,b in zip(cuts,cuts[1:]):
        cell=region.crop((a,0,b,region.height))
        # Descartar restos de figuras vecinas que invaden el recorte irregular.
        arr=np.array(cell)
        mask=arr[:,:,3]>32
        seen=np.zeros(mask.shape,dtype=bool)
        components=[]
        for yy,xx in zip(*np.where(mask)):
            if seen[yy,xx]: continue
            q=deque([(int(yy),int(xx))]); seen[yy,xx]=True; pixels=[]
            while q:
                y,x=q.popleft(); pixels.append((y,x))
                for dy,dx in ((-1,0),(1,0),(0,-1),(0,1)):
                    ny,nx=y+dy,x+dx
                    if 0<=ny<mask.shape[0] and 0<=nx<mask.shape[1] and mask[ny,nx] and not seen[ny,nx]:
                        seen[ny,nx]=True;q.append((ny,nx))
            components.append(pixels)
        if components:
            main=max(components,key=len)
            ys,xs=zip(*main); left,right,top,bottom=min(xs),max(xs),min(ys),max(ys)
            keep=np.zeros(mask.shape,dtype=bool)
            for pixels in components:
                ys,xs=zip(*pixels)
                if pixels is main:
                    for y,x in pixels: keep[y,x]=True
            arr[:,:,3][~keep]=0
            cell=Image.fromarray(arr)
        bbox=cell.getbbox()
        if bbox is None: raise ValueError(('empty',sheet,y0,a,b))
        cell=cell.crop(bbox)
        cell.info['source_sheet']=sheet
        result.append(cell)
    return result

def main():
    global sheets
    SOURCES.mkdir(parents=True,exist_ok=True)
    for i,f in enumerate(FILES):
        dst=SOURCES/f'TitanBienestar_{i+1}.png'
        if not dst.exists(): shutil.copy2(TEMP/f'codex-clipboard-{f}.png',dst)
    sheets=[cleaned(SOURCES/f'TitanBienestar_{i+1}.png') for i in range(5)]
    seq={}
    def add(name,s,y0,y1,n,x0=0,x1=1): seq[name]=row(s,y0,y1,n,x0,x1)
    add('idle',0,0,150,4,0,.56)
    add('forwards',0,150,287,6,0,.83)
    add('backwards',0,287,430,6,0,.83)
    # La caminata inversa entregada mira a la izquierda: debe retroceder mirando a la derecha.
    seq['backwards']=[im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for im in seq['backwards']]
    add('run',0,430,570,8)
    add('dash',0,570,667,4,0,.62)
    add('backdash',0,667,805,4,0,.63)
    add('crouch',0,805,947,3,0,.40)
    add('idle-turn',0,947,1083,3,0,.40)
    add('crouch-turn',0,1083,1196,3,0,.40)
    add('jump-start',0,1060,1196,2,.43,.68)
    add('jump-up',0,1196,1337,6)
    add('jump-land',0,1337,1448,3,0,.41)
    add('light-punch',1,0,205,4,0,.70)
    add('med-kick',1,205,427,5,0,.89)
    add('heavy-punch',1,427,642,5,0,.91)
    add('heavy-kick',1,642,859,5,0,.94)
    add('light-kick',1,859,1053,6)
    add('sweep',1,1053,1240,6)
    add('long-kick',1,1240,1448,6)
    seq['med-punch']=seq['light-punch']
    add('crouch-punch',2,0,158,4,0,.50)
    add('crouch-kick',2,0,158,4,.50,1)
    add('crouch-hp',2,158,359,5,0,.69)
    add('sweep',2,359,522,6)
    add('air-punch',2,522,739,4,0,.51)
    add('air-kick',2,522,739,4,.51,1)
    add('jump-roll',2,739,921,7)
    add('jump-back',2,921,1090,7)
    add('block-high',2,1090,1250,4,0,.49)
    add('block-low',2,1090,1250,4,.49,1)
    add('parry-high',2,1250,1448,3,0,.47)
    add('parry-low',2,1250,1448,3,.47,.90)
    add('hit-face',3,0,214,7)
    add('hit-stomach',3,214,427,7)
    add('thrown',3,427,658,7)
    add('fall',3,658,817,5)
    add('getup',3,817,1004,6)
    add('stun',3,1004,1201,8)
    add('hurt-crouch',3,214,427,2,.74,1)
    add('special-light',4,0,175,7)
    add('special-medium',4,175,355,7)
    add('special',4,355,539,6)
    add('super',4,539,730,7)
    add('counter',4,730,917,6,0,.78)
    add('victory',4,917,1083,6,0,.81)
    add('power-throw',4,1083,1261,7)
    add('taunt',4,1261,1448,8)
    seq['grab']=seq['counter'][:2]
    seq['throw']=seq['power-throw']
    seq['power-grab']=seq['counter']
    seq['overhead']=seq['crouch-hp']
    seq['idle-relaxed']=seq['idle']
    seq['talk']=seq['taunt']
    # Efecto separado: extremo de la ráfaga, sin la mano ni el personaje.
    im=sheets[4]
    projectile=im.crop((round(.765*im.width),round(.025*im.height),round(.90*im.width),round(.085*im.height)))
    projectile=projectile.crop(projectile.getbbox())
    seq['proj-fly']=[projectile,projectile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)]
    seq['proj-hit']=[projectile,projectile.resize((max(1,round(projectile.width*.8)),max(1,round(projectile.height*1.2)))),projectile.resize((max(1,round(projectile.width*.4)),max(1,round(projectile.height*.6))))]

    data=json.loads((ASSETS/'DATA/escomboy.json').read_text(encoding='utf-8'))
    groups={}
    for key in data['frames']:
        prefix=re.sub(r'-\d+$','',key)
        if key=='jump-start/land': prefix='jump-start'
        groups.setdefault(prefix,[]).append(key)
    scale=100/np.median([im.height for im in seq['idle']])
    scales=[100/np.median([im.height for im in seq[name]]) for name in ('idle','light-punch','block-high')]
    scales += [100/seq['hit-face'][0].height,100/seq['special-light'][0].height]
    atlas=Image.new('RGBA',(2560,6656))
    review=Image.new('RGB',(800,((len(groups)+3)//4)*140),(34,34,40))
    draw=ImageDraw.Draw(review)
    for gi,(prefix,keys) in enumerate(groups.items()):
        source=seq[prefix]
        # Orden numérico: escomboy inserta algunos frames de ataques al final del atlas.
        keys=sorted(keys,key=lambda k: 0 if k=='jump-start/land' else int(k.rsplit('-',1)[1]))
        for i,key in enumerate(keys):
            sprite=source[round(i*(len(source)-1)/max(1,len(keys)-1))]
            pose_scale=scales[sprite.info.get('source_sheet',4)]
            w,h=max(1,round(sprite.width*pose_scale)),max(1,round(sprite.height*pose_scale))
            if w>240 or h>210: raise ValueError(('oversize',key,w,h))
            sprite=sprite.resize((w,h),Image.Resampling.NEAREST)
            canvas=Image.new('RGBA',(256,256))
            bottom=128 if prefix=='talk' else 224
            canvas.alpha_composite(sprite,(128-w//2,bottom-h))
            x,y,_,_=data['frames'][key]['src']
            atlas.alpha_composite(canvas,(x,y))
            if i==len(keys)//2:
                rx,ry=(gi%4)*200,(gi//4)*140
                review.paste(sprite,(rx+100-w//2,ry+118-h),sprite)
                draw.text((rx+5,ry+121),prefix,fill='white')
    atlas.save(ASSETS/'IMAGES/TitanBienestar.webp','WEBP',lossless=True,method=6)
    (ASSETS/'DATA/titan_bienestar.json').write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    from compact_sf_atlas import compact_character
    compact_character(ASSETS, 'titan_bienestar', 'TitanBienestar')
    review.save(SOURCES/'revision.png')
    report={'frames':len(data['frames']),'animations':len(data['animations']),'scale':scale,'adaptations':['med-punch desde jab','agarres y lanzamientos reutilizan invocacion','overhead desde golpe ascendente','talk y reposo reutilizan poses','especiales conservan mecanica generica de proyectil'],'source_sequences':{k:len(v) for k,v in seq.items()}}
    (SOURCES/'importacion.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))
    from repair_titan_specials import repair
    repair(PROJECT)
    from repair_titan_attacks import repair as repair_attacks
    repair_attacks(PROJECT)
    from repair_titan_victory import repair as repair_victory
    repair_victory(PROJECT)

if __name__=='__main__': main()
