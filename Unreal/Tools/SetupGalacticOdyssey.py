"""One-time editor setup for the Galactic Odyssey Unreal project.

Run inside the Unreal Editor (Python Script Plugin enabled - it is, via the .uproject):
    Tools > Execute Python Script...  and pick this file, or in the Output Log console:
    py "<repo>/Unreal/Tools/SetupGalacticOdyssey.py" [--legacy-root <dir>] [--refresh-tables] [--force-materials]

What it does (idempotent):
  1. Creates /Game/GalacticOdyssey/Materials/M_Greybox (tintable "Color" parameter, ISM-ready) and
     M_PlanetSurface (vertex colour -> base colour, lit), the materials the settings point at.
  2. Imports every legacy libGDX model referenced by a kit row's LegacySourcePath
     (e.g. models/props/interior/chair_crew.glb) from <repo>/core/src/main/resources (or any
     folder given with --legacy-root, e.g. "D:/Big Space Game": the manifest path is tried first,
     then a file of the same name, .glb/.gltf/.fbx/.obj, anywhere below it) into
     /Game/GalacticOdyssey/Kit/<Category>/<AssetId>, and writes the imported mesh path back into
     Plugins/GalacticProcGen/Data/AssetKit.json so the JSON stays the reviewable source of truth.
  3. Creates the DataTables listed in Project Settings > Galactic Universe from the JSON files
     (only missing ones, unless --refresh-tables is given, which overwrites in-editor edits).

Missing legacy files are reported and skipped; their kit rows keep using engine placeholders.
"""

import json
import os
import re
import sys

import unreal

GAME_ROOT = "/Game/GalacticOdyssey"
MATERIAL_DIR = GAME_ROOT + "/Materials"
DATA_DIR = GAME_ROOT + "/Data"
KIT_DIR = GAME_ROOT + "/Kit"

# Formats the editor imports, most preferred first (glTF keeps PBR materials best).
MODEL_EXTENSIONS = (".glb", ".gltf", ".fbx", ".obj")
SKIPPED_DIRS = {"node_modules", "build", "Binaries", "Intermediate", "Saved", "DerivedDataCache", "__pycache__"}

TABLES = [
    # (asset name, json file, row struct name in Python)
    ("DT_AssetKit", "AssetKit.json", "GOAssetKitRow"),
    ("DT_HubRooms", "HubRooms.json", "GOHubRoomTemplate"),
    ("DT_PlanetTypes", "PlanetTypes.json", "GOPlanetTypeDef"),
    ("DT_SpectralClasses", "SpectralClasses.json", "GOSpectralClassDef"),
    ("DT_Biomes", "Biomes.json", "GOBiomeDef"),
    ("DT_NameLists", "NameLists.json", "GONameListDef"),
]

asset_tools = unreal.AssetToolsHelpers.get_asset_tools()
mel = unreal.MaterialEditingLibrary
eal = unreal.EditorAssetLibrary


def log(message):
    unreal.log("[GalacticOdyssey setup] " + message)


def warn(message):
    unreal.log_warning("[GalacticOdyssey setup] " + message)


def parse_args(argv):
    options = {"legacy_root": None, "refresh_tables": False, "force_materials": False}
    index = 0
    while index < len(argv):
        arg = argv[index]
        if arg == "--legacy-root" and index + 1 < len(argv):
            # Re-join a path with spaces ("Big Space Game") in case the console split it.
            parts = []
            while index + 1 < len(argv) and not argv[index + 1].startswith("--"):
                parts.append(argv[index + 1])
                index += 1
            options["legacy_root"] = " ".join(parts).strip("\"'")
        elif arg == "--refresh-tables":
            options["refresh_tables"] = True
        elif arg == "--force-materials":
            options["force_materials"] = True
        index += 1
    return options


def project_paths(options):
    project_dir = os.path.abspath(unreal.Paths.convert_relative_path_to_full(unreal.Paths.project_dir()))
    plugin_data = os.path.join(project_dir, "Plugins", "GalacticProcGen", "Data")
    legacy_root = options["legacy_root"] or os.path.join(os.path.dirname(project_dir), "core", "src", "main", "resources")
    return project_dir, plugin_data, os.path.abspath(legacy_root)


# --------------------------------------------------------------------------------------------
# Materials
# --------------------------------------------------------------------------------------------
def make_material(name, build, force):
    path = "{}/{}".format(MATERIAL_DIR, name)
    if eal.does_asset_exist(path):
        if not force:
            log("{} exists, keeping it".format(path))
            return
        eal.delete_asset(path)
    material = asset_tools.create_asset(name, MATERIAL_DIR, unreal.Material, unreal.MaterialFactoryNew())
    build(material)
    material.set_editor_property("used_with_instanced_static_meshes", True)
    mel.recompile_material(material)
    eal.save_loaded_asset(material)
    log("created " + path)


def build_greybox(material):
    color = mel.create_material_expression(material, unreal.MaterialExpressionVectorParameter, -500, 0)
    color.set_editor_property("parameter_name", "Color")
    color.set_editor_property("default_value", unreal.LinearColor(0.6, 0.6, 0.62, 1.0))
    mel.connect_material_property(color, "", unreal.MaterialProperty.MP_BASE_COLOR)
    roughness = mel.create_material_expression(material, unreal.MaterialExpressionConstant, -500, 250)
    roughness.set_editor_property("r", 0.7)
    mel.connect_material_property(roughness, "", unreal.MaterialProperty.MP_ROUGHNESS)


def build_planet_surface(material):
    vertex_color = mel.create_material_expression(material, unreal.MaterialExpressionVertexColor, -500, 0)
    mel.connect_material_property(vertex_color, "", unreal.MaterialProperty.MP_BASE_COLOR)
    roughness = mel.create_material_expression(material, unreal.MaterialExpressionConstant, -500, 250)
    roughness.set_editor_property("r", 0.85)
    mel.connect_material_property(roughness, "", unreal.MaterialProperty.MP_ROUGHNESS)


# --------------------------------------------------------------------------------------------
# Legacy model import
# --------------------------------------------------------------------------------------------
def mesh_volume(mesh):
    box = mesh.get_bounding_box()
    size = box.max - box.min
    return abs(size.x * size.y * size.z)


def index_model_files(legacy_root):
    """Maps lower-case file stem -> paths of importable models anywhere under legacy_root."""
    index = {}
    for directory, subdirs, files in os.walk(legacy_root):
        subdirs[:] = [name for name in subdirs if name not in SKIPPED_DIRS and not name.startswith(".")]
        for name in files:
            stem, extension = os.path.splitext(name)
            if extension.lower() in MODEL_EXTENSIONS + (".g3db", ".g3dj"):
                index.setdefault(stem.lower(), []).append(os.path.join(directory, name))
    return index


def find_legacy_file(legacy_root, source, index_cache):
    """Finds a kit row's legacy model under legacy_root, whatever the folder layout.

    Tries the manifest path relative to the root, to a libGDX project copy at the root, then any
    file with the same name (.glb, .gltf, .fbx or .obj) anywhere under the root.
    """
    relative = source.replace("\\", "/")
    for prefix in ("", "core/src/main/resources", "assets", "core/assets"):
        candidate = os.path.join(legacy_root, prefix, relative)
        if os.path.isfile(candidate):
            return candidate, None

    if "index" not in index_cache:
        index_cache["index"] = index_model_files(legacy_root)
    stem = os.path.splitext(os.path.basename(relative))[0].lower()
    matches = index_cache["index"].get(stem, [])
    importable = [path for path in matches if os.path.splitext(path)[1].lower() in MODEL_EXTENSIONS]
    if not importable:
        if matches:
            return None, "only libGDX .g3db/.g3dj files found ({}); Unreal imports .glb, .gltf, .fbx or " \
                         ".obj, so re-export the model from its source file".format(", ".join(matches))
        return None, "not found under " + legacy_root

    def rank(path):
        # Prefer the manifest's folder structure, then the preferred format, then the shortest path.
        normalised = path.replace("\\", "/").lower()
        extension = os.path.splitext(path)[1].lower()
        same_dirs = normalised.endswith(os.path.splitext(relative)[0].lower() + extension)
        return (not same_dirs, MODEL_EXTENSIONS.index(extension), len(path))

    importable.sort(key=rank)
    if len(importable) > 1:
        log("several files match {}; using {}".format(relative, importable[0]))
    return importable[0], None


def import_legacy_models(kit_rows, legacy_root):
    """Imports the models referenced by kit rows; returns {asset id: static mesh path}."""
    imported = {}
    tasks = []
    index_cache = {}
    for row in kit_rows:
        source = row.get("LegacySourcePath")
        if not source:
            continue
        file_path, problem = find_legacy_file(legacy_root, source, index_cache)
        if not file_path:
            warn("legacy model for {} ({}): {}".format(row["Name"], source, problem))
            continue
        log("found {} at {}".format(row["Name"], file_path))
        task = unreal.AssetImportTask()
        task.set_editor_property("filename", file_path)
        task.set_editor_property("destination_path", "{}/{}".format(KIT_DIR, row.get("Category", "Prop")))
        task.set_editor_property("destination_name", row["Name"])
        task.set_editor_property("automated", True)
        task.set_editor_property("replace_existing", True)
        task.set_editor_property("save", True)
        tasks.append((row["Name"], task))

    if not tasks:
        return imported
    asset_tools.import_asset_tasks([task for _, task in tasks])

    for asset_id, task in tasks:
        meshes = []
        for object_path in task.get_editor_property("imported_object_paths") or []:
            asset = eal.load_asset(object_path)
            if isinstance(asset, unreal.StaticMesh):
                meshes.append((mesh_volume(asset), object_path))
            elif isinstance(asset, unreal.SkeletalMesh):
                warn("{} imported as a skeletal mesh ({}); wrap it in a Blueprint actor and set the kit row's "
                     "ActorClass to use it".format(asset_id, object_path))
        if meshes:
            # glTF scenes can import as several meshes; the largest one is the asset body.
            imported[asset_id] = max(meshes)[1]
            log("imported {} -> {}".format(asset_id, imported[asset_id]))
        else:
            warn("no static mesh produced for " + asset_id)
    return imported


def enable_instancing(imported):
    """Kit meshes render through (H)ISM components, which need materials flagged for instancing.

    The editor sets the flag on demand, but cooked builds fall back to the default material, so
    flag the base materials of every imported mesh that live in the project.
    """
    done = set()
    for asset_id, mesh_path in imported.items():
        try:
            flag_mesh_materials(asset_id, mesh_path, done)
        except Exception as error:  # keep going: tables and other assets matter more than this flag
            warn("could not check the materials of {}: {}".format(asset_id, error))


def flag_mesh_materials(asset_id, mesh_path, done):
    mesh = eal.load_asset(mesh_path)
    for slot in mesh.get_editor_property("static_materials"):
        material = slot.get_editor_property("material_interface")
        base = material.get_base_material() if material else None
        if not base or base.get_path_name() in done:
            continue
        done.add(base.get_path_name())
        if base.get_editor_property("used_with_instanced_static_meshes"):
            continue
        if not base.get_path_name().startswith("/Game/"):
            warn("{}: material {} is outside /Game and isn't flagged for instanced meshes; packaged "
                 "builds may draw {} with the default material".format(asset_id, base.get_path_name(), asset_id))
            continue
        base.set_editor_property("used_with_instanced_static_meshes", True)
        mel.recompile_material(base)
        eal.save_loaded_asset(base)
        log("flagged {} for instanced meshes".format(base.get_path_name()))


def write_kit_json(kit_path, imported):
    """Records imported mesh paths in AssetKit.json with minimal textual edits (clean diffs)."""
    with open(kit_path, encoding="utf-8") as handle:
        text = handle.read()
    original = text
    for asset_id, mesh_path in imported.items():
        name_key = '"Name": "{}",'.format(asset_id)
        existing = re.compile(r'("Name": "{}",[^{{}}]*?"Mesh": ")[^"]*(")'.format(re.escape(asset_id)))
        if existing.search(text):
            text = existing.sub(lambda m: m.group(1) + mesh_path + m.group(2), text, count=1)
        else:
            text = text.replace(name_key, '{} "Mesh": "{}",'.format(name_key, mesh_path), 1)
    if text == original:
        return False
    json.loads(text)  # never write a broken file
    with open(kit_path, "w", encoding="utf-8") as handle:
        handle.write(text)
    log("updated mesh paths in " + kit_path)
    return True


# --------------------------------------------------------------------------------------------
# Data tables
# --------------------------------------------------------------------------------------------
def make_tables(plugin_data, refresh_all, refresh_names):
    for asset_name, json_file, struct_name in TABLES:
        path = "{}/{}".format(DATA_DIR, asset_name)
        exists = eal.does_asset_exist(path)
        if exists and not (refresh_all or asset_name in refresh_names):
            log("{} exists, keeping it (use --refresh-tables to rebuild from JSON)".format(path))
            continue
        row_struct = getattr(unreal, struct_name).static_struct()
        if exists:
            table = eal.load_asset(path)
        else:
            factory = unreal.DataTableFactory()
            factory.set_editor_property("struct", row_struct)
            table = asset_tools.create_asset(asset_name, DATA_DIR, unreal.DataTable, factory)
        with open(os.path.join(plugin_data, json_file), encoding="utf-8") as handle:
            text = handle.read()
        expected_rows = len(json.loads(text))
        clean = unreal.DataTableFunctionLibrary.fill_data_table_from_json_string(table, text, row_struct)
        # The importer reports every property a row leaves out ("missing an entry"), and the JSON
        # only lists non-default values, so judge the import by its rows instead of that flag.
        row_count = len(unreal.DataTableFunctionLibrary.get_data_table_row_names(table))
        if row_count != expected_rows:
            warn("could not fill {} from {} ({} of {} rows)".format(path, json_file, row_count, expected_rows))
            continue
        if not clean:
            log("{}: the 'missing an entry' warnings above are expected (defaults are left out of the JSON)".format(path))
        eal.save_loaded_asset(table)
        log("{} {} from {}".format("refreshed" if exists else "created", path, json_file))


def main():
    options = parse_args(sys.argv[1:])
    project_dir, plugin_data, legacy_root = project_paths(options)
    log("project: " + project_dir)
    log("legacy libGDX resources: " + legacy_root)

    make_material("M_Greybox", build_greybox, options["force_materials"])
    make_material("M_PlanetSurface", build_planet_surface, options["force_materials"])

    kit_path = os.path.join(plugin_data, "AssetKit.json")
    with open(kit_path, encoding="utf-8") as handle:
        kit_rows = json.load(handle)
    imported = import_legacy_models(kit_rows, legacy_root)
    enable_instancing(imported)
    kit_changed = write_kit_json(kit_path, imported)

    make_tables(plugin_data, options["refresh_tables"], {"DT_AssetKit"} if kit_changed else set())
    log("done. Press Play: the academy, home world and kit assets come from the generated universe.")


main()
