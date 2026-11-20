"""Celebración completa con calavera y pétalos, sin filtrar efectos separados."""
from pathlib import Path
import json
import numpy as np
from PIL import Image,ImageDraw

def repair(project):
    project=Path(project);assets=project/'app/src/main/assets/STREETFIGHTER';folder=project.parent/'newSFAssets/TitanBienestar'
    import shutil
    reference=folder/'victoria_referencia.png'
    if not reference.exists():shutil.copy2(Path(__import__('tempfile').gettempdir())/'codex-clipboard-4bc5aec4-e411-4b41-ba38-445a86d619da.png',reference)
    a=np.array(Image.open(reference).convert('RGBA'))
    a[:,:,3][a[:,:,:3].astype(int).sum(axis=2)<95]=0
    source=Image.fromarray(a)
    cuts=[0,135,280,435,620,810,1030,1173];centers=[50,195,345,505,690,890,1120]
    poses=[]
    for i,(left,right) in enumerate(zip(cuts,cuts[1:])):
        im=source.crop((left,0,right,225))
        pixels=np.array(im);mask=pixels[:,:,3]>0;seen=np.zeros(mask.shape,bool);components=[]
        from collections import deque
        for sy,sx in zip(*np.where(mask)):
            if seen[sy,sx]:continue
            q=deque([(sy,sx)]);seen[sy,sx]=True;part=[]
            while q:
                y,x=q.popleft();part.append((y,x))
                for dy,dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny,nx=y+dy,x+dx
                    if 0<=ny<mask.shape[0] and 0<=nx<mask.shape[1] and mask[ny,nx] and not seen[ny,nx]:seen[ny,nx]=True;q.append((ny,nx))
            components.append(part)
        main=max(components,key=len)
        for part in components:
            if part is main:continue
            if not any(pixels[y,x,0]>150 and pixels[y,x,2]>110 for y,x in part):
                for y,x in part:pixels[y,x,3]=0
        im=Image.fromarray(pixels);box=im.getbbox();im=im.crop(box)
        scale=.56;w,h=round(im.width*scale),round(im.height*scale)
        x=128-round((centers[i]-left-box[0])*scale);y=224-round((196-box[1])*scale)
        assert 0<=x and x+w<=320 and 0<=y and y+h<=256
        canvas=Image.new('RGBA',(320,256));canvas.alpha_composite(im.resize((w,h),Image.Resampling.NEAREST),(x,y));poses.append(canvas)
    dp=assets/'DATA/titan_bienestar.json';data=json.loads(dp.read_text(encoding='utf-8'))
    sheet=Image.open(assets/'IMAGES/TitanBienestar.webp').convert('RGBA');start=(sheet.height+1)//2*2
    atlas=Image.new('RGBA',(2560,start+256));atlas.paste(sheet,(0,0))
    ref=data['frames']['victory-1']
    for i,pose in enumerate(poses):
        frame=json.loads(json.dumps(ref));frame['src']=[i*320,start,320,256];frame['origin']=[128,224]
        data['frames'][f'victory-{i+1}']=frame;atlas.paste(pose,(i*320,start))
    delays=[10,10,12,15,12,15,20]
    data['animations']['victory']=[[f'victory-{i+1}',delay] for i,delay in enumerate(delays)]+[['victory-7',-1]]
    atlas.save(assets/'IMAGES/TitanBienestar.webp','WEBP',lossless=True,method=6)
    dp.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    from compact_sf_atlas import compact_character
    compact_character(assets,'titan_bienestar','TitanBienestar')
    frames=[]
    for im in poses:
        bg=Image.new('RGB',(320,256),(35,35,42));bg.paste(im,(0,0),im);ImageDraw.Draw(bg).line((0,224,320,224),fill=(80,100,110));frames.append(bg)
    frames[0].save(folder/'victoria_corregida.gif',save_all=True,append_images=frames[1:],duration=[round(d*1000/60) for d in delays],loop=0)
    contact=Image.new('RGB',(7*320,256),(35,35,42))
    for i,im in enumerate(frames):contact.paste(im,(i*320,0))
    contact.save(folder/'victoria_corregida.png')
    print('Victoria: siete poses completas, 1.57 segundos y pose final sostenida')

if __name__=='__main__':repair(Path(__file__).resolve().parents[1])
