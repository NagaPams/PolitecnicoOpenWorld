"""Separar overhead de upper agachado y calibrar la escala de sus poses."""
from pathlib import Path
import json
from PIL import Image,ImageDraw

def repair(project, already_scaled=False):
    project=Path(project);assets=project/'app/src/main/assets/STREETFIGHTER'
    dp=assets/'DATA/titan_bienestar.json';data=json.loads(dp.read_text(encoding='utf-8'))
    if data.get('titanAttackRepairVersion')==2:
        print('Correccion de golpes ya aplicada');return
    sheet=Image.open(assets/'IMAGES/TitanBienestar.webp').convert('RGBA')
    def cell(key):
        f=data['frames'][key];x,y,w,h=f['src']
        out=Image.new('RGBA',(320,256))
        out.alpha_composite(sheet.crop((x,y,x+w,y+h)),(128-f['origin'][0],224-f['origin'][1]))
        return out
    # Capturar antes de reemplazar: no acumular correcciones al ejecutar el importador.
    replacement={}
    for i,factor in enumerate([.80,.80,.78,.74,.70],1):
        if already_scaled:
            replacement[f'crouch-hp-{i}']=cell(f'crouch-hp-{i}')
            continue
        original=cell(f'crouch-hp-{i}');box=original.getbbox();pose=original.crop(box)
        w,h=round(pose.width*factor),round(pose.height*factor)
        origin_x=round((128-box[0])*factor);origin_y=round((224-box[1])*factor)
        pose=pose.resize((w,h),Image.Resampling.NEAREST)
        out=Image.new('RGBA',(320,256));out.alpha_composite(pose,(128-origin_x,224-origin_y))
        replacement[f'crouch-hp-{i}']=out
    # No existe una secuencia descendente dedicada en las hojas: usar el golpe
    # fuerte DE PIE como aproximación, sin insertar un upper de cuerpo agachado.
    overhead=[cell('light-punch-1'),cell('heavy-punch-2'),cell('heavy-punch-3'),cell('heavy-punch-4'),cell('light-punch-1')]
    for i,pose in enumerate(overhead,1):replacement[f'overhead-{i}']=pose
    # El directo medio debe mostrar la extensión del jab, no repetir solo la guardia.
    for i,source in enumerate([1,2,3,3,4,1],1):replacement[f'med-punch-{i}']=cell(f'light-punch-{source}')
    start=(sheet.height+1)//2*2
    atlas=Image.new('RGBA',(2560,start+2*256));atlas.paste(sheet,(0,0))
    for i,(key,im) in enumerate(replacement.items()):
        x,y=(i%8)*320,start+(i//8)*256
        atlas.paste(im,(x,y));data['frames'][key]['src']=[x,y,320,256];data['frames'][key]['origin']=[128,224]
    atlas.save(assets/'IMAGES/TitanBienestar.webp','WEBP',lossless=True,method=6)
    data['titanAttackRepairVersion']=2
    dp.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
    from compact_sf_atlas import compact_character
    compact_character(assets,'titan_bienestar','TitanBienestar')
    folder=project.parent/'newSFAssets/TitanBienestar'
    compare=Image.new('RGB',(6*320,3*276),(35,35,42));draw=ImageDraw.Draw(compare)
    idle=cell('idle-1')
    for row,prefix,count in [(0,'overhead',5),(1,'crouch-hp',5),(2,'med-punch',6)]:
        images=[replacement[f'{prefix}-{i}'] for i in range(1,count+1)]
        for i,im in enumerate(images):
            compare.paste(im,(i*320,row*276),im)
            draw.line((i*320,row*276+224,(i+1)*320,row*276+224),fill=(80,100,110))
            draw.text((i*320+6,row*276+253),f'{prefix}-{i+1}',fill='white')
        movie=[idle]+images+[idle]
        previews=[]
        for im in movie:
            bg=Image.new('RGB',(320,256),(35,35,42));bg.paste(im,(0,0),im);ImageDraw.Draw(bg).line((0,224,320,224),fill=(80,100,110));previews.append(bg)
        previews[0].save(folder/f'{prefix}_corregido.gif',save_all=True,append_images=previews[1:],duration=[220]+[110]*len(images)+[220],loop=0)
    compare.save(folder/'golpes_corregidos.png')
    print('Overhead separado del upper agachado; directo medio completo; vistas guardadas')

if __name__=='__main__':
    import sys
    repair(Path(__file__).resolve().parents[1],already_scaled='--keep-upper-scale' in sys.argv)
