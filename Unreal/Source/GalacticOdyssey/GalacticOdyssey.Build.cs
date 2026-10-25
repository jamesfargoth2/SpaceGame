using UnrealBuildTool;

public class GalacticOdyssey : ModuleRules
{
	public GalacticOdyssey(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;

		PublicDependencyModuleNames.AddRange(new string[]
		{
			"Core",
			"CoreUObject",
			"Engine",
			"InputCore",
			"EnhancedInput",
			"GalacticProcGen"
		});
	}
}
