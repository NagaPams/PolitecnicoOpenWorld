from pathlib import Path
import sys, shutil
from PIL import Image, ImageDraw
import numpy as np

root=Path(__file__).resolve().parents[2]
source=root/'newSFAssets/PrinterBonus'
source.mkdir(parents=True,exist_ok=True)
assets=root/'PolitecnicoOpenWorld/app/src/main/assets/STREETFIGHTER/IMAGES'
paths=[source/'printer_bonus_original.png',source/'printer_particles_original.png']
for i,(path,rows,cell,name) in enumerate(zip(paths,[2,4],[256,64],['printer_bonus','printer_particles'])):
    im=Image.open(path).convert('RGBA')
    atlas=Image.new('RGBA',(4*cell,rows*cell))
    for index in range(4*rows):
        col,row=index%4,index//4
        crop=im.crop((round(col*im.width/4),round(row*im.height/rows),round((col+1)*im.width/4),round((row+1)*im.height/rows)))
        a=np.array(crop)
        red=(a[:,:,0]>170)&(a[:,:,1]<100)&(a[:,:,2]<100)
        fringe=((a[:,:,2]>180)&(a[:,:,0]<100)&(a[:,:,1]<90))|((a[:,:,0]>160)&(a[:,:,2]>160)&(a[:,:,1]<100))
        a[:,:,3][red|fringe|(a[:,:,3]<40)]=0
        crop=Image.fromarray(a)
        if i==0:
            # Misma escala para todos los estados, ancla al piso de cada celda.
            crop=crop.resize((256,256),Image.Resampling.LANCZOS)
            bbox=crop.getbbox()
            fixed=Image.new('RGBA',(256,256))
            if bbox: fixed.alpha_composite(crop,(0,248-bbox[3]))
            crop=fixed
        else:
            bbox=crop.getbbox()
            crop=crop.crop(bbox) if bbox else crop
            crop.thumbnail((56,56),Image.Resampling.LANCZOS)
            fixed=Image.new('RGBA',(64,64))
            fixed.alpha_composite(crop,((64-crop.width)//2,(64-crop.height)//2))
            crop=fixed
        atlas.alpha_composite(crop,(col*cell,row*cell))
    atlas.save(assets/f'{name}.webp',lossless=True)
    atlas.save(source/f'{name}_prepared.png')
    print(name,atlas.size,(assets/f'{name}.webp').stat().st_size)
