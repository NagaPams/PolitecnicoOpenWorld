"""Recortes explícitos de especiales: conservar efectos separados y anclar al cuerpo."""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw

def repair(project):
    project=Path(project)
    assets=project/'app/src/main/assets/STREETFIGHTER'
    source=project.parent/'newSFAssets/TitanBienestar'
    a=np.array(Image.open(source/'TitanBienestar_5.png').convert('RGBA'))
    red=(a[:,:,0]>180)&(a[:,:,1]<65)&(a[:,:,2]<65)
    a[:,:,3][red|(a[:,:,3]<24)]=0
    sheet=Image.fromarray(a)
    # y inicial/final, divisiones X, centros del CUERPO, apoyo de los pies.
    config={
        'special-light':(0,175,[0,142,282,420,565,710,975,1086],[70,210,350,490,630,780,1025],163),
        'special-medium':(175,355,[0,143,290,430,615,785,957,1086],[70,215,355,510,670,830,1020],337),
        'special':(355,539,[0,125,305,505,730,950,1086],[65,205,405,605,825,1020],526),
        'super':(539,730,[0,145,290,500,710,960,1086],[65,215,355,550,765,1020],713),
    }
    poses={}; scale=100/160
    for prefix,(top,bottom,cuts,centers,feet) in config.items():
        poses[prefix]=[]
        for i,(left,right) in enumerate(zip(cuts,cuts[1:])):
            # Límites explícitos: no eliminar componentes separados del cuerpo.
            im=sheet.crop((left,top,right,bottom))
            pixels=np.array(im)
            xx=np.arange(im.width)[None,:]+left
            yy=np.arange(im.height)[:,None]
            if prefix=='special-light' and i==5:
                # La recuperación vecina invade el extremo inferior de la cola de fuego.
                pixels[:,:,3][(xx>925)&(yy>100)]=0
            im=Image.fromarray(pixels)
            box=im.getbbox();assert box
            im=im.crop(box)
            anchor=(centers[i]-left-box[0],feet-top-box[1])
            # Dibujar con margen a ambos lados: nunca centrar por el ancho del efecto.
            w,h=round(im.width*scale),round(im.height*scale)
            im=im.resize((w,h),Image.Resampling.NEAREST)
            x=128-round(anchor[0]*scale);y=224-round(anchor[1]*scale)
            assert x>=0 and y>=0 and x+w<=320 and y+h<=256,(prefix,i,x,y,w,h)
            canvas=Image.new('RGBA',(320,256));canvas.alpha_composite(im,(x,y))
            poses[prefix].append(canvas)
    data_path=assets/'DATA/titan_bienestar.json'
    data=json.loads(data_path.read_text(encoding='utf-8'))
    original=Image.open(assets/'IMAGES/TitanBienestar.webp').convert('RGBA')
    offset=(original.height+1)//2*2
    atlas=Image.new('RGBA',(max(original.width,2560),offset+4*256))
    atlas.paste(original,(0,0))
    for ri,(prefix,frames) in enumerate(poses.items()):
        indices=list(range(len(frames))) if prefix!='super' else [0,1,2,2,3,4,4,5]
        reference=data['frames'][f'{prefix}-1']
        for i,fi in enumerate(indices):
            key=f'{prefix}-{i+1}'; frame=json.loads(json.dumps(data['frames'].get(key,reference)))
            frame['src']=[i*320,offset+ri*256,320,256];frame['origin']=[128,224]
            data['frames'][key]=frame
            atlas.paste(frames[fi],(i*320,offset+ri*256))
    timings={
        'special1Light':('special-light',[3,5,5,5,6,8,10]),
        'special1Medium':('special-medium',[4,6,6,7,8,10,13]),
        'special1Heavy':('special',[5,8,10,12,15,15]),
        'superArt':('super',[5,5,6,7,8,7,6,10]),
    }
    for anim,(prefix,delays) in timings.items():
        data['animations'][anim]=[[f'{prefix}-{i+1}',delay] for i,delay in enumerate(delays)]
        data['animations'][anim].append([f'{prefix}-{len(delays)}',-1])
    data['events']['projectile']['light']['frame']=5
    data['events']['projectile']['medium']['frame']=3
    data['events']['projectile']['heavy']['frame']=3
    atlas.save(assets/'IMAGES/TitanBienestar.webp','WEBP',lossless=True,method=6)
    data_path.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    from compact_sf_atlas import compact_character
    result=compact_character(assets,'titan_bienestar','TitanBienestar')
    review=Image.new('RGB',(8*320,4*276),(35,35,42));draw=ImageDraw.Draw(review)
    for ri,(prefix,frames) in enumerate(poses.items()):
        for i,frame in enumerate(frames):
            review.paste(frame,(i*320,ri*276),frame)
            draw.line((i*320,ri*276+224,(i+1)*320,ri*276+224),fill=(70,100,110))
            draw.text((i*320+8,ri*276+252),f'{prefix} {i+1}',fill='white')
    review.save(source/'especiales_corregidos.png')
    # Vista temporal alineada al mismo suelo; no implica ejecución en dispositivo.
    for name in ['special1Light','special1Medium','special1Heavy','superArt']:
        prefix,delays=timings[name]
        frames=poses[prefix]
        if prefix=='super':frames=[frames[i] for i in [0,1,2,2,3,4,4,5]]
        output=[]
        for f in frames:
            bg=Image.new('RGB',(384,280),(35,35,42));bg.paste(f,(32,0),f)
            ImageDraw.Draw(bg).line((0,224,384,224),fill=(90,90,100));output.append(bg)
        output[0].save(source/f'{name}_revision.gif',save_all=True,append_images=output[1:],duration=[round(d*1000/60) for d in delays],loop=0)
    print('Especiales completos, alineados y con todas las poses:',result)

if __name__=='__main__': repair(Path(__file__).resolve().parents[1])
