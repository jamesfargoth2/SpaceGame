#pragma once

#include "CoreMinimal.h"
#include "Hub/GOHubTypes.h"

struct FGOContentDefs;

/**
 * Lays out the academy hub where the player graduates.
 *
 * Plan: the graduation hall (atrium template) sits at the origin with its stage wall facing
 * Params.FrontDirection. Up to three spoke corridors leave the other sides. Mandatory side rooms
 * (dormitory, classroom, simulator bay, ...) are packed along the spokes nearest the hall first,
 * then optional rooms by weight; terminal rooms (hangar, observation deck) cap the spoke ends.
 * Every room opens onto its corridor through a door, so the whole hub is connected by
 * construction. Walls, doors and windows are derived from the cell grid, then each room's
 * prop rules place kit assets and emit NPC / player / ship spawn points.
 *
 * Output is plain data (FGOHubLayout); AGOAcademyHubActor turns it into instanced meshes.
 */
class GALACTICPROCGEN_API FGOHubGenerator
{
public:
	explicit FGOHubGenerator(const FGOContentDefs& InContent) : Content(InContent) {}

	FGOHubLayout Generate(uint64 Seed, const FGOHubGenParams& Params, const FString& AcademyName) const;

private:
	const FGOContentDefs& Content;
};
