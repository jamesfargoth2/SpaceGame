using UnrealBuildTool;

public class GalacticOdysseyTarget : TargetRules
{
	public GalacticOdysseyTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Game;
		DefaultBuildSettings = BuildSettingsVersion.Latest;
		IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
		ExtraModuleNames.Add("GalacticOdyssey");
	}
}
