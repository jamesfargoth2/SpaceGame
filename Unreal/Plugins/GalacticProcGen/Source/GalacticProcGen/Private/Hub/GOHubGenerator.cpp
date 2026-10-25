#include "Hub/GOHubGenerator.h"
#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "Universe/GONameGenerator.h"

namespace GOHubGeneratorPrivate
{
	/** Owner value of corridor cells in the occupancy grid (rooms use their index). */
	constexpr int32 CorridorOwner = -2;

	const FIntPoint Directions[4] = { FIntPoint(1, 0), FIntPoint(0, 1), FIntPoint(-1, 0), FIntPoint(0, -1) };

	/** Right-hand side of a grid direction in Unreal's frame (X forward, Y right). */
	FORCEINLINE FIntPoint RightOf(const FIntPoint& D) { return FIntPoint(-D.Y, D.X); }

	FORCEINLINE FIntPoint Scale(const FIntPoint& D, int32 S) { return FIntPoint(D.X * S, D.Y * S); }

	FORCEINLINE double YawDegrees(const FIntPoint& D) { return FMath::RadiansToDegrees(FMath::Atan2(static_cast<double>(D.Y), static_cast<double>(D.X))); }

	FORCEINLINE FVector ToVector(const FIntPoint& D) { return FVector(D.X, D.Y, 0.0); }

	FIntPoint SnapToCardinal(const FIntPoint& D)
	{
		if (D.X == 0 && D.Y == 0)
		{
			return FIntPoint(1, 0);
		}
		if (FMath::Abs(D.X) >= FMath::Abs(D.Y))
		{
			return FIntPoint(D.X > 0 ? 1 : -1, 0);
		}
		return FIntPoint(0, D.Y > 0 ? 1 : -1);
	}

	/** Key of the edge shared by two edge-adjacent cells. */
	FIntVector EdgeKey(const FIntPoint& A, const FIntPoint& B)
	{
		if (A.X != B.X)
		{
			return FIntVector(FMath::Min(A.X, B.X), A.Y, 0);
		}
		return FIntVector(A.X, FMath::Min(A.Y, B.Y), 1);
	}

	/** Axis-aligned XY bounds of a footprint (X forward, Y right) rotated by YawDeg. */
	FBox2D FootprintBox(const FVector& Center, const FVector& Footprint, double YawDeg)
	{
		const double Rad = FMath::DegreesToRadians(YawDeg);
		const double C = FMath::Abs(FMath::Cos(Rad));
		const double S = FMath::Abs(FMath::Sin(Rad));
		const double HalfX = (C * Footprint.X + S * Footprint.Y) * 0.5;
		const double HalfY = (S * Footprint.X + C * Footprint.Y) * 0.5;
		return FBox2D(FVector2D(Center.X - HalfX, Center.Y - HalfY), FVector2D(Center.X + HalfX, Center.Y + HalfY));
	}

	bool Overlaps(const FBox2D& A, const FBox2D& B, double Epsilon = 1.0)
	{
		return A.Min.X < B.Max.X - Epsilon && A.Max.X > B.Min.X + Epsilon
			&& A.Min.Y < B.Max.Y - Epsilon && A.Max.Y > B.Min.Y + Epsilon;
	}

	bool Contains(const FBox2D& Outer, const FBox2D& Inner, double Epsilon = 1.0)
	{
		return Inner.Min.X >= Outer.Min.X - Epsilon && Inner.Max.X <= Outer.Max.X + Epsilon
			&& Inner.Min.Y >= Outer.Min.Y - Epsilon && Inner.Max.Y <= Outer.Max.Y + Epsilon;
	}

	struct FSpoke
	{
		FIntPoint Dir = FIntPoint(1, 0);
		FIntPoint Right = FIntPoint(0, 1);
		/** First corridor cell outside the hall (t = 0, lateral 0). */
		FIntPoint Start = FIntPoint(0, 0);
		/** Packed length on the [0] right and [1] left side of the corridor. */
		int32 Fill[2] = { 0, 0 };
		/** Number of corridor cells committed along the spoke. */
		int32 Length = 0;
		const FGOHubRoomTemplate* Terminal = nullptr;
	};

	struct FRoomExtra
	{
		const FGOHubRoomTemplate* Template = nullptr;
		FBox2D Interior = FBox2D(ForceInit);
		TArray<FBox2D> Keepout;
		TArray<FBox2D> Placed;
		/** Distance from the front wall already claimed by stage props, cm. */
		double FrontReservedCm = 0.0;
		/** Local front-axis coordinate of the row nearest the stage (for the player spawn). */
		double FrontRowF = 0.0;
		bool bHasRows = false;
	};

	class FBuilder
	{
	public:
		FBuilder(const FGOContentDefs& InContent, const FGOHubGenParams& InParams, uint64 InSeed)
			: Content(InContent)
			, Names(InContent)
			, Rng(GOSeed::Domain(InSeed, GOSeed::AcademyDomain))
			, Seed(InSeed)
		{
			Layout.Seed = GOSeed::ToBlueprint(InSeed);
			Layout.Params = InParams;
			FGOHubGenParams& P = Layout.Params;
			P.CellSizeCm = FMath::Max(P.CellSizeCm, 150.f);
			P.WallHeightCm = FMath::Max(P.WallHeightCm, 260.f);
			P.WallThicknessCm = FMath::Clamp(P.WallThicknessCm, 2.f, P.CellSizeCm * 0.25f);
			P.FloorThicknessCm = FMath::Max(P.FloorThicknessCm, 2.f);
			P.CorridorWidthCells = FMath::Clamp(P.CorridorWidthCells, 1, 3);
			P.SpokeCount = FMath::Clamp(P.SpokeCount, 1, 3);
			P.MinSpokeLengthCells = FMath::Max(P.MinSpokeLengthCells, 2);
			P.CorridorLightSpacingCells = FMath::Max(P.CorridorLightSpacingCells, 1);
			P.FrontDirection = SnapToCardinal(P.FrontDirection);

			LMin = -((P.CorridorWidthCells - 1) / 2);
			LMax = LMin + P.CorridorWidthCells - 1;
		}

		void Run();

		FGOHubLayout Layout;

	private:
		// --- layout ---
		const FGOHubRoomTemplate* FindAtriumTemplate() const;
		FIntPoint RollSize(const FGOHubRoomTemplate& Template);
		int32 AddRoom(const FGOHubRoomTemplate& Template, const TArray<FIntPoint>& Cells, EGOHubRoomPlacement Placement, int32 SpokeIndex, const FIntPoint& Front);
		void PlaceAtrium(const FGOHubRoomTemplate& Template);
		void SetupSpokes();
		FIntPoint SpokeCell(const FSpoke& Spoke, int32 T, int32 L) const { return Spoke.Start + Scale(Spoke.Dir, T) + Scale(Spoke.Right, L); }
		bool CorridorFree(int32 SpokeIndex, int32 FromT, int32 ToT) const;
		void CommitCorridor(int32 SpokeIndex, int32 ToT);
		bool TryPlaceSideRoom(const FGOHubRoomTemplate& Template, const FIntPoint& Size, bool bMandatory);
		void PlaceTerminal(int32 SpokeIndex);
		void AddDoor(int32 RoomIndex, const FIntPoint& RoomCell, const FIntPoint& OutsideCell);

		// --- geometry ---
		FVector CellCenter(const FIntPoint& Cell) const { return FVector(Cell.X * Layout.Params.CellSizeCm, Cell.Y * Layout.Params.CellSizeCm, 0.0); }
		void AddPiece(TArray<FGOHubPiece>& Target, FName AssetId, const FVector& Center, double YawDeg, const FVector& Size, int32 RoomIndex, const FLinearColor& Tint);
		void BuildStructure();
		void BuildLights();

		// --- props & spawns ---
		void PlaceProps();
		void ApplyRule(int32 RoomIndex, const FGOPropRule& Rule);
		bool TryPlace(int32 RoomIndex, const FGOPropRule& Rule, const FVector& Footprint, double F, double R, double LocalYawDeg);
		void AddSpawn(EGOHubSpawnRole Role, const FVector& Feet, double YawDeg, int32 RoomIndex, bool bSeated, FName AssetId);
		void PlacePlayer();

		const FGOContentDefs& Content;
		FGONameGenerator Names;
		FGORandom Rng;
		uint64 Seed;
		int32 LMin = 0;
		int32 LMax = 0;

		TMap<FIntPoint, int32> Owner;
		TMap<FIntPoint, int32> CorridorSpoke;
		TSet<FIntVector> DoorEdges;
		TArray<FSpoke> Spokes;
		TArray<FRoomExtra> Extras;
		FGOHubRoomTemplate DefaultAtrium;
	};

	const FGOHubRoomTemplate* FBuilder::FindAtriumTemplate() const
	{
		for (const FGOHubRoomTemplate& Template : Content.HubRooms)
		{
			if (Template.Placement == EGOHubRoomPlacement::Atrium)
			{
				return &Template;
			}
		}
		return nullptr;
	}

	FIntPoint FBuilder::RollSize(const FGOHubRoomTemplate& Template)
	{
		const int32 MinX = FMath::Max(Template.MinSizeCells.X, 1);
		const int32 MinY = FMath::Max(Template.MinSizeCells.Y, 1);
		return FIntPoint(
			Rng.RandRange(MinX, FMath::Max(MinX, Template.MaxSizeCells.X)),
			Rng.RandRange(MinY, FMath::Max(MinY, Template.MaxSizeCells.Y)));
	}

	int32 FBuilder::AddRoom(const FGOHubRoomTemplate& Template, const TArray<FIntPoint>& Cells,
		EGOHubRoomPlacement Placement, int32 SpokeIndex, const FIntPoint& Front)
	{
		FIntPoint Min = Cells[0];
		FIntPoint Max = Cells[0];
		for (const FIntPoint& Cell : Cells)
		{
			Min.X = FMath::Min(Min.X, Cell.X);
			Min.Y = FMath::Min(Min.Y, Cell.Y);
			Max.X = FMath::Max(Max.X, Cell.X);
			Max.Y = FMath::Max(Max.Y, Cell.Y);
		}

		const int32 Index = Layout.Rooms.Num();
		FGOHubRoom& Room = Layout.Rooms.AddDefaulted_GetRef();
		Room.TemplateId = Template.TemplateId;
		Room.DisplayName = Template.DisplayName.IsEmpty() ? Template.TemplateId.ToString() : Template.DisplayName;
		Room.Placement = Placement;
		Room.SpokeIndex = SpokeIndex;
		Room.MinCell = Min;
		Room.SizeCells = FIntPoint(Max.X - Min.X + 1, Max.Y - Min.Y + 1);
		Room.FrontDirection = Front;
		Room.Tint = Template.Tint;

		for (const FIntPoint& Cell : Cells)
		{
			Owner.Add(Cell, Index);
		}

		const FGOHubGenParams& P = Layout.Params;
		const double Half = P.CellSizeCm * 0.5;
		const double Inset = P.WallThicknessCm * 0.5;
		const FVector2D BoxMin(Min.X * P.CellSizeCm - Half + Inset, Min.Y * P.CellSizeCm - Half + Inset);
		const FVector2D BoxMax(Max.X * P.CellSizeCm + Half - Inset, Max.Y * P.CellSizeCm + Half - Inset);
		Room.CenterCm = FVector((BoxMin.X + BoxMax.X) * 0.5, (BoxMin.Y + BoxMax.Y) * 0.5, 0.0);
		const FVector2D Extent = BoxMax - BoxMin;
		Room.InteriorSizeCm = Front.X != 0 ? FVector2D(Extent.X, Extent.Y) : FVector2D(Extent.Y, Extent.X);

		FRoomExtra& Extra = Extras.AddDefaulted_GetRef();
		Extra.Template = &Template;
		Extra.Interior = FBox2D(BoxMin, BoxMax);
		return Index;
	}

	void FBuilder::AddDoor(int32 RoomIndex, const FIntPoint& RoomCell, const FIntPoint& OutsideCell)
	{
		FGOHubDoor& Door = Layout.Rooms[RoomIndex].Doors.AddDefaulted_GetRef();
		Door.Cell = RoomCell;
		Door.OutsideCell = OutsideCell;
		DoorEdges.Add(EdgeKey(RoomCell, OutsideCell));

		// Keep the doorway clear of props: a box reaching ~1.8 m into the room.
		const FGOHubGenParams& P = Layout.Params;
		const FVector EdgeCenter = (CellCenter(RoomCell) + CellCenter(OutsideCell)) * 0.5;
		const FVector Inward = ToVector(RoomCell - OutsideCell);
		const FVector ZoneCenter = EdgeCenter + Inward * 90.0;
		const FVector ZoneSize(180.0, P.CellSizeCm, 1.0);
		Extras[RoomIndex].Keepout.Add(FootprintBox(ZoneCenter, ZoneSize, YawDegrees(RoomCell - OutsideCell)));
	}

	void FBuilder::PlaceAtrium(const FGOHubRoomTemplate& Template)
	{
		const FIntPoint Front = Layout.Params.FrontDirection;
		const FIntPoint Right = RightOf(Front);
		const FIntPoint Size = RollSize(Template);	// X = width across the hall, Y = depth toward the stage
		const int32 Width = FMath::Max(Size.X, 3);
		const int32 Depth = FMath::Max(Size.Y, 3);

		TArray<FIntPoint> Cells;
		for (int32 F = -(Depth / 2); F <= Depth - 1 - Depth / 2; ++F)
		{
			for (int32 R = -(Width / 2); R <= Width - 1 - Width / 2; ++R)
			{
				Cells.Add(Scale(Front, F) + Scale(Right, R));
			}
		}
		Layout.GraduationHallIndex = AddRoom(Template, Cells, EGOHubRoomPlacement::Atrium, INDEX_NONE, Front);
	}

	void FBuilder::SetupSpokes()
	{
		const FIntPoint Front = Layout.Params.FrontDirection;
		const FIntPoint Right = RightOf(Front);
		const FIntPoint Back(-Front.X, -Front.Y);
		const FIntPoint Left(-Right.X, -Right.Y);

		TArray<FIntPoint> Dirs;
		switch (Layout.Params.SpokeCount)
		{
		case 1:  Dirs = { Back }; break;
		case 2:  Dirs = { Right, Left }; break;
		default: Dirs = { Back, Right, Left }; break;
		}

		const int32 Hall = Layout.GraduationHallIndex;
		for (const FIntPoint& Dir : Dirs)
		{
			FSpoke& Spoke = Spokes.AddDefaulted_GetRef();
			Spoke.Dir = Dir;
			Spoke.Right = RightOf(Dir);

			int32 Extent = 0;
			while (const int32* Found = Owner.Find(Scale(Dir, Extent + 1)))
			{
				if (*Found != Hall)
				{
					break;
				}
				++Extent;
			}
			Spoke.Start = Scale(Dir, Extent + 1);

			const int32 SpokeIndex = Spokes.Num() - 1;
			CommitCorridor(SpokeIndex, 1);
			for (int32 L = LMin; L <= LMax; ++L)
			{
				const FIntPoint Outside = SpokeCell(Spoke, 0, L);
				const FIntPoint Inside = Outside - Dir;
				const int32* InsideOwner = Owner.Find(Inside);
				if (InsideOwner && *InsideOwner == Hall)
				{
					AddDoor(Hall, Inside, Outside);
				}
			}
		}
	}

	bool FBuilder::CorridorFree(int32 SpokeIndex, int32 FromT, int32 ToT) const
	{
		const FSpoke& Spoke = Spokes[SpokeIndex];
		for (int32 T = FMath::Max(FromT, 0); T < ToT; ++T)
		{
			for (int32 L = LMin; L <= LMax; ++L)
			{
				const FIntPoint Cell = SpokeCell(Spoke, T, L);
				if (const int32* Found = CorridorSpoke.Find(Cell))
				{
					if (*Found != SpokeIndex)
					{
						return false;
					}
				}
				else if (Owner.Contains(Cell))
				{
					return false;
				}
			}
		}
		return true;
	}

	void FBuilder::CommitCorridor(int32 SpokeIndex, int32 ToT)
	{
		FSpoke& Spoke = Spokes[SpokeIndex];
		for (int32 T = Spoke.Length; T < ToT; ++T)
		{
			for (int32 L = LMin; L <= LMax; ++L)
			{
				const FIntPoint Cell = SpokeCell(Spoke, T, L);
				Owner.Add(Cell, CorridorOwner);
				CorridorSpoke.Add(Cell, SpokeIndex);
				Layout.CorridorCells.Add(Cell);
			}
		}
		Spoke.Length = FMath::Max(Spoke.Length, ToT);
	}

	bool FBuilder::TryPlaceSideRoom(const FGOHubRoomTemplate& Template, const FIntPoint& Size, bool bMandatory)
	{
		const int32 Width = FMath::Max(Size.X, 1);	// along the corridor
		const int32 Depth = FMath::Max(Size.Y, 1);	// away from it

		// Least-filled spoke sides first, so the hub grows evenly and important rooms stay close.
		struct FSlot { int32 Spoke; int32 Side; int32 Fill; };
		TArray<FSlot> Slots;
		for (int32 S = 0; S < Spokes.Num(); ++S)
		{
			for (int32 Side = 0; Side < 2; ++Side)
			{
				Slots.Add({ S, Side, Spokes[S].Fill[Side] });
			}
		}
		Slots.StableSort([](const FSlot& A, const FSlot& B) { return A.Fill < B.Fill; });

		for (const FSlot& Slot : Slots)
		{
			FSpoke& Spoke = Spokes[Slot.Spoke];
			const bool bRight = Slot.Side == 0;
			const int32 LFrom = bRight ? LMax + 1 : LMin - Depth;
			const int32 LTo = bRight ? LMax + Depth : LMin - 1;

			for (int32 Offset = Spoke.Fill[Slot.Side]; Offset < Spoke.Fill[Slot.Side] + 24; ++Offset)
			{
				TArray<FIntPoint> Cells;
				bool bFree = true;
				for (int32 T = Offset; T < Offset + Width && bFree; ++T)
				{
					for (int32 L = LFrom; L <= LTo; ++L)
					{
						const FIntPoint Cell = SpokeCell(Spoke, T, L);
						if (Owner.Contains(Cell))
						{
							bFree = false;
							break;
						}
						Cells.Add(Cell);
					}
				}
				if (!bFree || !CorridorFree(Slot.Spoke, Spoke.Length, Offset + Width))
				{
					continue;
				}

				CommitCorridor(Slot.Spoke, Offset + Width);
				const FIntPoint Front = bRight ? Spoke.Right : FIntPoint(-Spoke.Right.X, -Spoke.Right.Y);
				const int32 RoomIndex = AddRoom(Template, Cells, EGOHubRoomPlacement::Side, Slot.Spoke, Front);

				const int32 DoorWidth = FMath::Clamp(Template.DoorWidthCells, 1, Width);
				const int32 DoorStart = Offset + (Width - DoorWidth) / 2;
				for (int32 T = DoorStart; T < DoorStart + DoorWidth; ++T)
				{
					AddDoor(RoomIndex,
						SpokeCell(Spoke, T, bRight ? LMax + 1 : LMin - 1),
						SpokeCell(Spoke, T, bRight ? LMax : LMin));
				}

				Spoke.Fill[Slot.Side] = Offset + Width;
				return true;
			}
		}

		Layout.Warnings.Add(FString::Printf(TEXT("%s room '%s' could not be placed."),
			bMandatory ? TEXT("Mandatory") : TEXT("Optional"), *Template.TemplateId.ToString()));
		return false;
	}

	void FBuilder::PlaceTerminal(int32 SpokeIndex)
	{
		FSpoke& Spoke = Spokes[SpokeIndex];
		const FGOHubGenParams& P = Layout.Params;
		int32 Length = FMath::Max3(P.MinSpokeLengthCells, Spoke.Fill[0], Spoke.Fill[1]);
		Length = FMath::Max(Length, Spoke.Length);

		if (!Spoke.Terminal)
		{
			// Dead end: extend the corridor as far as it is free (it ends in a window).
			int32 Reach = Spoke.Length;
			while (Reach < Length && CorridorFree(SpokeIndex, Reach, Reach + 1))
			{
				++Reach;
			}
			CommitCorridor(SpokeIndex, Reach);
			return;
		}

		const FGOHubRoomTemplate& Template = *Spoke.Terminal;
		const FIntPoint Size = RollSize(Template);	// X = width across the spoke, Y = depth along it
		const int32 Width = FMath::Max(Size.X, P.CorridorWidthCells);
		const int32 Depth = FMath::Max(Size.Y, 1);
		const int32 LStart = LMin - (Width - P.CorridorWidthCells) / 2;

		for (int32 Try = 0; Try < 24; ++Try, ++Length)
		{
			if (!CorridorFree(SpokeIndex, Spoke.Length, Length))
			{
				break;
			}

			TArray<FIntPoint> Cells;
			bool bFree = true;
			for (int32 T = Length; T < Length + Depth && bFree; ++T)
			{
				for (int32 L = LStart; L < LStart + Width; ++L)
				{
					const FIntPoint Cell = SpokeCell(Spoke, T, L);
					if (Owner.Contains(Cell))
					{
						bFree = false;
						break;
					}
					Cells.Add(Cell);
				}
			}
			if (!bFree)
			{
				continue;
			}

			CommitCorridor(SpokeIndex, Length);
			const int32 RoomIndex = AddRoom(Template, Cells, EGOHubRoomPlacement::Terminal, SpokeIndex, Spoke.Dir);
			const int32 DoorWidth = FMath::Clamp(Template.DoorWidthCells, 1, P.CorridorWidthCells);
			const int32 DoorStart = LMin + (P.CorridorWidthCells - DoorWidth) / 2;
			for (int32 L = DoorStart; L < DoorStart + DoorWidth; ++L)
			{
				AddDoor(RoomIndex, SpokeCell(Spoke, Length, L), SpokeCell(Spoke, Length - 1, L));
			}
			if (Template.TemplateId == FName(TEXT("Hangar")) && Layout.HangarIndex == INDEX_NONE)
			{
				Layout.HangarIndex = RoomIndex;
			}
			return;
		}

		Layout.Warnings.Add(FString::Printf(TEXT("Terminal room '%s' could not be placed."), *Template.TemplateId.ToString()));
		CommitCorridor(SpokeIndex, FMath::Max(Spoke.Length, P.MinSpokeLengthCells));
	}

	void FBuilder::AddPiece(TArray<FGOHubPiece>& Target, FName AssetId, const FVector& Center, double YawDeg,
		const FVector& Size, int32 RoomIndex, const FLinearColor& Tint)
	{
		if (AssetId.IsNone())
		{
			return;
		}
		FGOHubPiece& Piece = Target.AddDefaulted_GetRef();
		Piece.AssetId = AssetId;
		Piece.Transform = FTransform(FRotator(0.0, YawDeg, 0.0), Center);
		Piece.SlotSizeCm = Size;
		Piece.RoomIndex = RoomIndex;
		Piece.Tint = Tint;

		const FBox2D Box = FootprintBox(Center, Size, YawDeg);
		Layout.BoundsCm += FVector(Box.Min.X, Box.Min.Y, Center.Z - Size.Z * 0.5);
		Layout.BoundsCm += FVector(Box.Max.X, Box.Max.Y, Center.Z + Size.Z * 0.5);
	}

	void FBuilder::BuildStructure()
	{
		const FGOHubGenParams& P = Layout.Params;
		const double C = P.CellSizeCm;
		const double H = P.WallHeightCm;
		const double T = P.WallThicknessCm;
		const double FT = P.FloorThicknessCm;

		// Deterministic order independent of map internals.
		TArray<FIntPoint> Cells;
		Owner.GenerateKeyArray(Cells);
		Cells.Sort([](const FIntPoint& A, const FIntPoint& B) { return A.Y != B.Y ? A.Y < B.Y : A.X < B.X; });

		for (const FIntPoint& Cell : Cells)
		{
			const int32 CellOwner = Owner.FindChecked(Cell);
			const bool bRoom = CellOwner >= 0;
			const FGOHubRoomTemplate* Template = bRoom ? Extras[CellOwner].Template : nullptr;
			const FLinearColor Tint = bRoom ? Layout.Rooms[CellOwner].Tint : P.CorridorTint;
			const FVector Center = CellCenter(Cell);

			AddPiece(Layout.Structure, bRoom ? Template->FloorAsset : P.CorridorFloorAsset,
				Center + FVector(0.0, 0.0, -FT * 0.5), 0.0, FVector(C, C, FT), CellOwner, Tint);
			AddPiece(Layout.Structure, bRoom ? Template->CeilingAsset : P.CorridorCeilingAsset,
				Center + FVector(0.0, 0.0, H + FT * 0.5), 0.0, FVector(C, C, FT), CellOwner, Tint);

			for (const FIntPoint& Dir : Directions)
			{
				const FIntPoint Neighbor = Cell + Dir;
				const int32* NeighborOwner = Owner.Find(Neighbor);
				const FVector WallCenter = Center + ToVector(Dir) * (C * 0.5) + FVector(0.0, 0.0, H * 0.5);
				const FVector WallSize(T, C + T, H);
				const double Yaw = YawDegrees(Dir);

				if (!NeighborOwner)
				{
					FName Asset;
					if (bRoom)
					{
						const bool bFront = Layout.Rooms[CellOwner].FrontDirection == Dir && !Template->FrontWallAsset.IsNone();
						Asset = bFront ? Template->FrontWallAsset : Template->ExteriorWallAsset;
					}
					else
					{
						const int32 Phase = P.CorridorWindowEvery > 0
							? ((Cell.X + Cell.Y) % P.CorridorWindowEvery + P.CorridorWindowEvery) % P.CorridorWindowEvery
							: 1;
						Asset = P.CorridorWindowEvery > 0 && Phase == 0 ? P.CorridorWindowAsset : P.CorridorWallAsset;
					}
					AddPiece(Layout.Structure, Asset, WallCenter, Yaw, WallSize, CellOwner, Tint);
					continue;
				}

				// Interior edges are emitted once, from the cell on their negative side.
				if (*NeighborOwner == CellOwner || Dir.X < 0 || Dir.Y < 0)
				{
					continue;
				}

				const int32 RoomSide = bRoom ? CellOwner : *NeighborOwner;
				const FGOHubRoomTemplate* SideTemplate = RoomSide >= 0 ? Extras[RoomSide].Template : nullptr;
				const FLinearColor SideTint = RoomSide >= 0 ? Layout.Rooms[RoomSide].Tint : P.CorridorTint;
				const bool bDoor = DoorEdges.Contains(EdgeKey(Cell, Neighbor));
				FName Asset = P.CorridorWallAsset;
				if (SideTemplate)
				{
					Asset = bDoor ? SideTemplate->DoorAsset : SideTemplate->WallAsset;
				}
				AddPiece(Layout.Structure, Asset, WallCenter, Yaw, WallSize, RoomSide, SideTint);
			}
		}
	}

	void FBuilder::BuildLights()
	{
		const FGOHubGenParams& P = Layout.Params;
		const double C = P.CellSizeCm;
		const double LightZ = P.WallHeightCm - 40.0;
		const FVector FixtureSize = Content.GetFootprint(P.LightFixtureAsset);

		auto AddLight = [&](const FVector& XY, double Radius, const FLinearColor& Color, int32 RoomIndex)
		{
			FGOHubLight& Light = Layout.Lights.AddDefaulted_GetRef();
			Light.LocationCm = FVector(XY.X, XY.Y, LightZ);
			Light.AttenuationRadiusCm = Radius;
			Light.IntensityLumens = P.LightIntensityLumens;
			Light.Color = Color;
			Light.RoomIndex = RoomIndex;
			AddPiece(Layout.Props, P.LightFixtureAsset,
				FVector(XY.X, XY.Y, P.WallHeightCm - FixtureSize.Z * 0.5), 0.0, FixtureSize, RoomIndex, Color);
		};

		for (int32 RoomIndex = 0; RoomIndex < Layout.Rooms.Num(); ++RoomIndex)
		{
			const FGOHubRoom& Room = Layout.Rooms[RoomIndex];
			const int32 NX = FMath::Max(1, FMath::RoundToInt(Room.SizeCells.X / 3.0));
			const int32 NY = FMath::Max(1, FMath::RoundToInt(Room.SizeCells.Y / 3.0));
			const double SpanX = Room.SizeCells.X * C;
			const double SpanY = Room.SizeCells.Y * C;
			const double MinX = Room.MinCell.X * C - C * 0.5;
			const double MinY = Room.MinCell.Y * C - C * 0.5;
			const double Radius = FMath::Max(SpanX / NX, SpanY / NY) * 1.2 + 400.0;
			const FLinearColor Color = Extras[RoomIndex].Template->LightColor;
			for (int32 IY = 0; IY < NY; ++IY)
			{
				for (int32 IX = 0; IX < NX; ++IX)
				{
					AddLight(FVector(MinX + (IX + 0.5) * SpanX / NX, MinY + (IY + 0.5) * SpanY / NY, 0.0), Radius, Color, RoomIndex);
				}
			}
		}

		const int32 Spacing = P.CorridorLightSpacingCells;
		for (const FSpoke& Spoke : Spokes)
		{
			for (int32 T = Spacing / 2; T < Spoke.Length; T += Spacing)
			{
				const FVector A = CellCenter(SpokeCell(Spoke, T, LMin));
				const FVector B = CellCenter(SpokeCell(Spoke, T, LMax));
				AddLight((A + B) * 0.5, C * Spacing * 1.2 + 300.0, FLinearColor(0.85f, 0.92f, 1.f, 1.f), INDEX_NONE);
			}
		}
	}

	void FBuilder::AddSpawn(EGOHubSpawnRole Role, const FVector& Feet, double YawDeg, int32 RoomIndex, bool bSeated, FName AssetId)
	{
		FGOHubSpawnPoint& Spawn = Layout.Spawns.AddDefaulted_GetRef();
		Spawn.Role = Role;
		Spawn.Transform = FTransform(FRotator(0.0, YawDeg, 0.0), FVector(Feet.X, Feet.Y, 0.0));
		Spawn.RoomIndex = RoomIndex;
		Spawn.bSeated = bSeated;
		Spawn.AssetId = AssetId;

		const uint64 NameSeed = GOSeed::ForId(GOSeed::Domain(Seed, GOSeed::NpcDomain), static_cast<uint64>(Layout.Spawns.Num()));
		switch (Role)
		{
		case EGOHubSpawnRole::Cadet:		Spawn.DisplayName = TEXT("Cadet ") + Names.PersonName(NameSeed); break;
		case EGOHubSpawnRole::Instructor:	Spawn.DisplayName = TEXT("Instructor ") + Names.PersonName(NameSeed); break;
		case EGOHubSpawnRole::Staff:		Spawn.DisplayName = Names.PersonName(NameSeed); break;
		case EGOHubSpawnRole::Ship:			Spawn.DisplayName = Names.ShipName(NameSeed); break;
		default: break;
		}
	}

	bool FBuilder::TryPlace(int32 RoomIndex, const FGOPropRule& Rule, const FVector& Footprint, double F, double R, double LocalYawDeg)
	{
		const FGOHubRoom& Room = Layout.Rooms[RoomIndex];
		FRoomExtra& Extra = Extras[RoomIndex];
		const FVector Front = ToVector(Room.FrontDirection);
		const FVector Right = ToVector(RightOf(Room.FrontDirection));
		const double WorldYaw = YawDegrees(Room.FrontDirection) + LocalYawDeg;
		const FVector Floor = Room.CenterCm + Front * F + Right * R;

		const FBox2D Box = FootprintBox(Floor, Footprint, WorldYaw);
		if (!Contains(Extra.Interior, Box))
		{
			return false;
		}
		for (const FBox2D& Other : Extra.Keepout)
		{
			if (Overlaps(Box, Other))
			{
				return false;
			}
		}
		for (const FBox2D& Other : Extra.Placed)
		{
			if (Overlaps(Box, Other))
			{
				return false;
			}
		}

		Extra.Placed.Add(Box);
		const FGOAssetKitRow* Kit = Content.FindAsset(Rule.AssetId);
		AddPiece(Layout.Props, Rule.AssetId, Floor + FVector(0.0, 0.0, Footprint.Z * 0.5), WorldYaw, Footprint,
			RoomIndex, Kit ? Kit->FallbackColor : FLinearColor::Gray);

		const bool bSpawn = Rng.FRand() < Rule.SpawnFraction;
		if (Rule.SpawnRole != EGOHubSpawnRole::None && bSpawn)
		{
			const FVector Facing = FRotator(0.0, WorldYaw, 0.0).Vector();
			const FName Visual = Rule.SpawnRole == EGOHubSpawnRole::Ship ? Rule.AssetId
				: Rule.SpawnRole == EGOHubSpawnRole::Player ? Layout.Params.PlayerAsset
				: Layout.Params.CadetAsset;
			if (Rule.bSeated || Rule.SpawnRole == EGOHubSpawnRole::Ship)
			{
				AddSpawn(Rule.SpawnRole, Floor, WorldYaw, RoomIndex, Rule.bSeated, Visual);
			}
			else
			{
				const double SpawnYaw = Rule.bSpawnFacesProp ? WorldYaw + 180.0 : WorldYaw;
				AddSpawn(Rule.SpawnRole, Floor + Facing * Rule.SpawnOffsetCm, SpawnYaw, RoomIndex, false, Visual);
			}
		}
		return true;
	}

	void FBuilder::ApplyRule(int32 RoomIndex, const FGOPropRule& Rule)
	{
		const bool bApply = Rng.FRand() < Rule.Chance;
		const int32 Count = Rng.RandRange(FMath::Max(Rule.MinCount, 0), FMath::Max(Rule.MinCount, Rule.MaxCount));
		if (!bApply || Count <= 0 || !Content.FindAsset(Rule.AssetId))
		{
			return;
		}

		FRoomExtra& Extra = Extras[RoomIndex];
		const FGOHubRoom& Room = Layout.Rooms[RoomIndex];
		const FVector Fp = Content.GetFootprint(Rule.AssetId);
		const double HF = Room.InteriorSizeCm.X * 0.5;
		const double HR = Room.InteriorSizeCm.Y * 0.5;
		const double Margin = Rule.MarginCm;
		const double Spacing = Rule.SpacingCm;
		int32 Placed = 0;

		switch (Rule.Pattern)
		{
		case EGOPropPattern::Stage:
		{
			const double F = HF - Margin - Fp.X * 0.5;
			for (int32 K = 0; K < Count; ++K)
			{
				const double R = (K - (Count - 1) * 0.5) * (Fp.Y + Spacing);
				Placed += TryPlace(RoomIndex, Rule, Fp, F, R, 180.0) ? 1 : 0;
			}
			if (Placed > 0)
			{
				Extra.FrontReservedCm = FMath::Max(Extra.FrontReservedCm, Margin + Fp.X + 150.0);
			}
			break;
		}
		case EGOPropPattern::Rows:
		{
			const double PitchF = Fp.X + Spacing;
			const double PitchR = Fp.Y + Spacing;
			const double FFront = HF - FMath::Max(Margin, Extra.FrontReservedCm) - Fp.X * 0.5;
			const double FBack = -HF + Margin + 150.0 + Fp.X * 0.5;
			const double RLimit = HR - Margin - Fp.Y * 0.5;
			const double Aisle = HR * 2.0 > 600.0 ? 160.0 : 0.0;

			TArray<double> Columns;
			if (Aisle > 0.0)
			{
				for (double R = Aisle * 0.5 + Fp.Y * 0.5; R <= RLimit; R += PitchR)
				{
					Columns.Add(-R);
					Columns.Add(R);
				}
			}
			else if (RLimit >= 0.0)
			{
				const int32 N = FMath::FloorToInt(2.0 * RLimit / PitchR) + 1;
				for (int32 K = 0; K < N; ++K)
				{
					Columns.Add((K - (N - 1) * 0.5) * PitchR);
				}
			}
			Columns.Sort();

			for (double F = FFront; F >= FBack && Placed < Count; F -= PitchF)
			{
				for (const double R : Columns)
				{
					if (Placed >= Count)
					{
						break;
					}
					if (TryPlace(RoomIndex, Rule, Fp, F, R, 0.0))
					{
						if (!Extra.bHasRows)
						{
							Extra.bHasRows = true;
							Extra.FrontRowF = F;
						}
						++Placed;
					}
				}
			}
			break;
		}
		case EGOPropPattern::AlongWalls:
		{
			struct FWallRun { double Fixed; bool bAlongR; double LocalYaw; double Length; };
			const FWallRun Runs[4] = {
				{ HF - Margin - Fp.X * 0.5, true, 180.0, HR },		// front wall
				{ HR - Margin - Fp.X * 0.5, false, -90.0, HF },		// right wall
				{ -HF + Margin + Fp.X * 0.5, true, 0.0, HR },		// back wall
				{ -HR + Margin + Fp.X * 0.5, false, 90.0, HF },		// left wall
			};
			const int32 FirstWall = Rng.RandHelper(4);
			for (int32 W = 0; W < 4 && Placed < Count; ++W)
			{
				const FWallRun& Run = Runs[(FirstWall + W) % 4];
				const double Pitch = Fp.Y + Spacing;
				for (double S = -Run.Length + Fp.Y * 0.5 + 20.0; S <= Run.Length - Fp.Y * 0.5 - 20.0 && Placed < Count; S += Pitch)
				{
					const double F = Run.bAlongR ? Run.Fixed : S;
					const double R = Run.bAlongR ? S : Run.Fixed;
					Placed += TryPlace(RoomIndex, Rule, Fp, F, R, Run.LocalYaw) ? 1 : 0;
				}
			}
			break;
		}
		case EGOPropPattern::Ring:
		{
			const double Radius = FMath::Min(HF, HR) - Margin - FMath::Max(Fp.X, Fp.Y) * 0.5;
			if (Radius < 50.0)
			{
				TryPlace(RoomIndex, Rule, Fp, 0.0, 0.0, 0.0);
				break;
			}
			for (int32 K = 0; K < Count; ++K)
			{
				const double Angle = 2.0 * UE_DOUBLE_PI * K / Count;
				const double F = FMath::Cos(Angle) * Radius;
				const double R = FMath::Sin(Angle) * Radius;
				const double FaceCenter = FMath::RadiansToDegrees(FMath::Atan2(-R, -F));
				TryPlace(RoomIndex, Rule, Fp, F, R, FaceCenter);
			}
			break;
		}
		case EGOPropPattern::Center:
			TryPlace(RoomIndex, Rule, Fp, 0.0, 0.0, 0.0);
			break;
		case EGOPropPattern::Corners:
		{
			const double F = HF - Margin - Fp.X * 0.5;
			const double R = HR - Margin - Fp.Y * 0.5;
			const double Corners[4][2] = { { F, R }, { F, -R }, { -F, R }, { -F, -R } };
			for (int32 K = 0; K < FMath::Min(Count, 4); ++K)
			{
				const double CF = Corners[K][0];
				const double CR = Corners[K][1];
				TryPlace(RoomIndex, Rule, Fp, CF, CR, FMath::RadiansToDegrees(FMath::Atan2(-CR, -CF)));
			}
			break;
		}
		case EGOPropPattern::Scatter:
		default:
		{
			const double FRange = HF - Margin - FMath::Max(Fp.X, Fp.Y) * 0.5;
			const double RRange = HR - Margin - FMath::Max(Fp.X, Fp.Y) * 0.5;
			if (FRange <= 0.0 || RRange <= 0.0)
			{
				break;
			}
			for (int32 Attempt = 0; Attempt < Count * 12 && Placed < Count; ++Attempt)
			{
				const double F = Rng.DRandRange(-FRange, FRange);
				const double R = Rng.DRandRange(-RRange, RRange);
				const double Yaw = 90.0 * Rng.RandRange(0, 3);
				Placed += TryPlace(RoomIndex, Rule, Fp, F, R, Yaw) ? 1 : 0;
			}
			break;
		}
		}
	}

	void FBuilder::PlaceProps()
	{
		for (int32 RoomIndex = 0; RoomIndex < Layout.Rooms.Num(); ++RoomIndex)
		{
			const FGOHubRoomTemplate* Template = Extras[RoomIndex].Template;
			// Stage props first so audience rows know how much of the front is taken.
			for (int32 Pass = 0; Pass < 2; ++Pass)
			{
				for (const FGOPropRule& Rule : Template->Props)
				{
					const bool bStage = Rule.Pattern == EGOPropPattern::Stage;
					if ((Pass == 0) == bStage)
					{
						ApplyRule(RoomIndex, Rule);
					}
				}
			}
		}
	}

	void FBuilder::PlacePlayer()
	{
		const int32 Hall = Layout.GraduationHallIndex;
		const FGOHubRoom& Room = Layout.Rooms[Hall];
		const FRoomExtra& Extra = Extras[Hall];
		const FVector Front = ToVector(Room.FrontDirection);

		// Stand in the centre aisle level with the front row, facing the stage.
		double F = Extra.bHasRows ? Extra.FrontRowF : -Room.InteriorSizeCm.X * 0.25;
		const double MinF = -Room.InteriorSizeCm.X * 0.5 + 60.0;
		for (; F > MinF; F -= 50.0)
		{
			const FVector Feet = Room.CenterCm + Front * F;
			const FBox2D Body = FootprintBox(Feet, FVector(70.0, 70.0, 1.0), 0.0);
			bool bClear = true;
			for (const FBox2D& Other : Extra.Placed)
			{
				bClear &= !Overlaps(Body, Other);
			}
			if (bClear)
			{
				break;
			}
		}
		AddSpawn(EGOHubSpawnRole::Player, Room.CenterCm + Front * F, YawDegrees(Room.FrontDirection), Hall, false, Layout.Params.PlayerAsset);
	}

	void FBuilder::Run()
	{
		const FGOHubRoomTemplate* Atrium = FindAtriumTemplate();
		if (!Atrium)
		{
			Layout.Warnings.Add(TEXT("No atrium room template; using a bare graduation hall."));
			DefaultAtrium.TemplateId = TEXT("GraduationHall");
			DefaultAtrium.DisplayName = TEXT("Graduation Hall");
			DefaultAtrium.Placement = EGOHubRoomPlacement::Atrium;
			DefaultAtrium.MinSizeCells = FIntPoint(7, 7);
			DefaultAtrium.MaxSizeCells = FIntPoint(7, 7);
			Atrium = &DefaultAtrium;
		}

		PlaceAtrium(*Atrium);
		SetupSpokes();

		// Terminal rooms: mandatory ones by priority, then optional ones by weight for free spoke ends.
		TArray<const FGOHubRoomTemplate*> Terminals;
		TArray<const FGOHubRoomTemplate*> OverflowSide;
		{
			TArray<const FGOHubRoomTemplate*> Mandatory;
			for (const FGOHubRoomTemplate& Template : Content.HubRooms)
			{
				if (Template.Placement == EGOHubRoomPlacement::Terminal)
				{
					for (int32 K = 0; K < Template.MinCount; ++K)
					{
						Mandatory.Add(&Template);
					}
				}
			}
			Mandatory.StableSort([](const FGOHubRoomTemplate& A, const FGOHubRoomTemplate& B) { return A.Priority > B.Priority; });
			for (const FGOHubRoomTemplate* Template : Mandatory)
			{
				(Terminals.Num() < Spokes.Num() ? Terminals : OverflowSide).Add(Template);
			}

			TArray<const FGOHubRoomTemplate*> Optional;
			TArray<float> Weights;
			for (const FGOHubRoomTemplate& Template : Content.HubRooms)
			{
				if (Template.Placement == EGOHubRoomPlacement::Terminal && Template.MaxCount > Template.MinCount)
				{
					Optional.Add(&Template);
					Weights.Add(Template.Weight);
				}
			}
			while (Terminals.Num() < Spokes.Num() && Optional.Num() > 0)
			{
				const int32 Pick = Rng.PickWeighted(Weights);
				if (Pick == INDEX_NONE)
				{
					break;
				}
				Terminals.Add(Optional[Pick]);
				Optional.RemoveAt(Pick);
				Weights.RemoveAt(Pick);
			}
		}
		for (int32 S = 0; S < Spokes.Num() && S < Terminals.Num(); ++S)
		{
			Spokes[S].Terminal = Terminals[S];
		}

		// Side rooms: mandatory first (highest priority nearest the hall), then optional by weight.
		{
			TArray<const FGOHubRoomTemplate*> Mandatory = OverflowSide;
			for (const FGOHubRoomTemplate& Template : Content.HubRooms)
			{
				if (Template.Placement == EGOHubRoomPlacement::Side)
				{
					for (int32 K = 0; K < Template.MinCount; ++K)
					{
						Mandatory.Add(&Template);
					}
				}
			}
			Mandatory.StableSort([](const FGOHubRoomTemplate& A, const FGOHubRoomTemplate& B) { return A.Priority > B.Priority; });
			for (const FGOHubRoomTemplate* Template : Mandatory)
			{
				TryPlaceSideRoom(*Template, RollSize(*Template), true);
			}

			TArray<const FGOHubRoomTemplate*> Optional;
			TArray<float> Weights;
			TArray<int32> Remaining;
			for (const FGOHubRoomTemplate& Template : Content.HubRooms)
			{
				if (Template.Placement == EGOHubRoomPlacement::Side && Template.MaxCount > Template.MinCount)
				{
					Optional.Add(&Template);
					Weights.Add(Template.Weight);
					Remaining.Add(Template.MaxCount - Template.MinCount);
				}
			}
			for (int32 K = 0; K < Layout.Params.OptionalRoomCount; ++K)
			{
				const int32 Pick = Rng.PickWeighted(Weights);
				if (Pick == INDEX_NONE)
				{
					break;
				}
				TryPlaceSideRoom(*Optional[Pick], RollSize(*Optional[Pick]), false);
				if (--Remaining[Pick] <= 0)
				{
					Weights[Pick] = 0.f;
				}
			}
		}

		for (int32 S = 0; S < Spokes.Num(); ++S)
		{
			PlaceTerminal(S);
		}

		BuildStructure();
		BuildLights();
		PlaceProps();
		PlacePlayer();
	}
}

FGOHubLayout FGOHubGenerator::Generate(uint64 Seed, const FGOHubGenParams& Params, const FString& AcademyName) const
{
	GOHubGeneratorPrivate::FBuilder Builder(Content, Params, Seed);
	Builder.Layout.AcademyName = AcademyName;
	Builder.Run();
	return MoveTemp(Builder.Layout);
}

const FGOHubSpawnPoint* FGOHubLayout::FindSpawn(EGOHubSpawnRole Role) const
{
	return Spawns.FindByPredicate([Role](const FGOHubSpawnPoint& Spawn) { return Spawn.Role == Role; });
}

int32 FGOHubLayout::CountRooms(FName TemplateId) const
{
	int32 Count = 0;
	for (const FGOHubRoom& Room : Rooms)
	{
		Count += Room.TemplateId == TemplateId ? 1 : 0;
	}
	return Count;
}

FVector FGOHubLayout::CellCenterCm(const FIntPoint& Cell) const
{
	return FVector(Cell.X * Params.CellSizeCm, Cell.Y * Params.CellSizeCm, 0.0);
}
