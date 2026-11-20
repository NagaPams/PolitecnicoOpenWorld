"""Reconstrucción aislada del Titan desde sus PNG, sin modificar producción."""
from pathlib import Path
import shutil

def build_titan(recipe, output, root):
    import import_titan_bienestar as importer
    work = output / 'source-build'
    project = work / 'PolitecnicoOpenWorld'
    assets = project / 'app/src/main/assets/STREETFIGHTER'
    sources = work / 'newSFAssets/TitanBienestar'
    sources.mkdir(parents=True, exist_ok=True)
    for name in recipe['sheets']:
        original = (root / name).resolve()
        if not original.is_relative_to(root.resolve()) or not original.is_file():
            raise ValueError('Fuente inexistente o fuera del repositorio')
        shutil.copy2(original, sources / original.name)
    (assets / 'DATA').mkdir(parents=True, exist_ok=True)
    (assets / 'IMAGES').mkdir(parents=True, exist_ok=True)
    template = (root / recipe['source_template']).resolve()
    if not template.is_relative_to(root.resolve()) or not template.is_file():
        raise ValueError('Template fuera del repositorio o inexistente')
    shutil.copy2(template, assets / 'DATA/escomboy.json')
    importer.ROOT, importer.PROJECT = work, project
    importer.ASSETS, importer.SOURCES = assets, sources
    importer.main()
    shutil.copy2(assets / 'DATA/titan_bienestar.json', output / 'DATA/titan_bienestar.json')
    shutil.copy2(assets / 'IMAGES/TitanBienestar.webp', output / 'IMAGES/TitanBienestar.webp')
