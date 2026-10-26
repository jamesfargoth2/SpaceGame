using System.IO;
using UnrealBuildTool;

public class GalacticProcGen : ModuleRules
{
	public GalacticProcGen(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;

		// Private sources include each other by path from the Private root (e.g. "Tests/...").
		PrivateIncludePaths.Add(Path.Combine(ModuleDirectory, "Private"));

		PublicDependencyModuleNames.AddRange(new string[]
		{
			"Core",
			"CoreUObject",
			"Engine",
			"DeveloperSettings",
			"ProceduralMeshComponent"
		});

		PrivateDependencyModuleNames.AddRange(new string[]
		{
			"Json",
			"JsonUtilities",
			"Projects"
		});

		// Default content definitions (planet types, hub rooms, asset kit, ...) are loose JSON
		// files read at runtime when no DataTable override is configured. Stage them as UFS so
		// they end up in the pak of packaged builds.
		RuntimeDependencies.Add(Path.Combine(PluginDirectory, "Data", "*.json"), StagedFileType.UFS);
	}
}
