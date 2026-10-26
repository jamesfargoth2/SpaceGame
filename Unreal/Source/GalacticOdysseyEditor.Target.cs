using UnrealBuildTool;

public class GalacticOdysseyEditorTarget : TargetRules
{
	public GalacticOdysseyEditorTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Editor;
		DefaultBuildSettings = BuildSettingsVersion.Latest;
		IncludeOrderVersion = EngineIncludeOrderVersion.Latest;
		ExtraModuleNames.Add("GalacticOdyssey");
	}
}
