#include "Preview.h"

#include "Core/GOContentDefs.h"
#include "Core/GORandom.h"
#include "Planet/GOPlanetTerrain.h"
#include "Universe/GOGalaxyGenerator.h"
#include "Universe/GOStarSystemGenerator.h"
#include "Universe/GOStartScenario.h"

#include <array>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <sstream>

namespace GOHeadless
{
	namespace
	{
		std::string Hex(const FLinearColor& C)
		{
			char Buffer[8];
			std::snprintf(Buffer, sizeof(Buffer), "#%02x%02x%02x",
				static_cast<int>(FMath::Clamp(C.R, 0.f, 1.f) * 255.f),
				static_cast<int>(FMath::Clamp(C.G, 0.f, 1.f) * 255.f),
				static_cast<int>(FMath::Clamp(C.B, 0.f, 1.f) * 255.f));
			return Buffer;
		}

		std::string Escape(const std::string& S)
		{
			std::string Out;
			for (const char C : S)
			{
				switch (C)
				{
				case '&': Out += "&amp;"; break;
				case '<': Out += "&lt;"; break;
				case '>': Out += "&gt;"; break;
				case '"': Out += "&quot;"; break;
				default: Out += C; break;
				}
			}
			return Out;
		}

		// ---- PNG (stored deflate blocks: no compression library needed) ----
		uint32 Crc32(const unsigned char* Data, size_t Size, uint32 Crc = 0xFFFFFFFFu)
		{
			for (size_t I = 0; I < Size; ++I)
			{
				Crc ^= Data[I];
				for (int K = 0; K < 8; ++K)
				{
					Crc = (Crc >> 1) ^ (0xEDB88320u & (0u - (Crc & 1u)));
				}
			}
			return Crc;
		}

		void PutU32(std::vector<unsigned char>& Out, uint32 V)
		{
			Out.push_back(static_cast<unsigned char>(V >> 24));
			Out.push_back(static_cast<unsigned char>(V >> 16));
			Out.push_back(static_cast<unsigned char>(V >> 8));
			Out.push_back(static_cast<unsigned char>(V));
		}

		void PutChunk(std::vector<unsigned char>& Out, const char* Type, const std::vector<unsigned char>& Data)
		{
			PutU32(Out, static_cast<uint32>(Data.size()));
			std::vector<unsigned char> Body(Type, Type + 4);
			Body.insert(Body.end(), Data.begin(), Data.end());
			Out.insert(Out.end(), Body.begin(), Body.end());
			PutU32(Out, Crc32(Body.data(), Body.size()) ^ 0xFFFFFFFFu);
		}

		bool WritePng(const std::string& Path, int Width, int Height, const std::vector<unsigned char>& Rgb)
		{
			std::vector<unsigned char> Raw;
			Raw.reserve(static_cast<size_t>(Height) * (Width * 3 + 1));
			for (int Y = 0; Y < Height; ++Y)
			{
				Raw.push_back(0);
				Raw.insert(Raw.end(), Rgb.begin() + static_cast<long>(Y) * Width * 3, Rgb.begin() + static_cast<long>(Y + 1) * Width * 3);
			}

			std::vector<unsigned char> Z = { 0x78, 0x01 };
			uint32 A = 1, B = 0;
			for (const unsigned char Byte : Raw)
			{
				A = (A + Byte) % 65521u;
				B = (B + A) % 65521u;
			}
			for (size_t Offset = 0; Offset < Raw.size(); Offset += 65535)
			{
				const size_t Len = std::min<size_t>(65535, Raw.size() - Offset);
				Z.push_back(Offset + Len >= Raw.size() ? 1 : 0);
				Z.push_back(static_cast<unsigned char>(Len & 0xFF));
				Z.push_back(static_cast<unsigned char>(Len >> 8));
				Z.push_back(static_cast<unsigned char>(~Len & 0xFF));
				Z.push_back(static_cast<unsigned char>((~Len >> 8) & 0xFF));
				Z.insert(Z.end(), Raw.begin() + static_cast<long>(Offset), Raw.begin() + static_cast<long>(Offset + Len));
			}
			PutU32(Z, (B << 16) | A);

			std::vector<unsigned char> Png = { 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
			std::vector<unsigned char> Header;
			PutU32(Header, static_cast<uint32>(Width));
			PutU32(Header, static_cast<uint32>(Height));
			Header.insert(Header.end(), { 8, 2, 0, 0, 0 });
			PutChunk(Png, "IHDR", Header);
			PutChunk(Png, "IDAT", Z);
			PutChunk(Png, "IEND", {});

			std::ofstream File(Path, std::ios::binary);
			File.write(reinterpret_cast<const char*>(Png.data()), static_cast<std::streamsize>(Png.size()));
			return static_cast<bool>(File);
		}

		// ---- Academy plan ----
		bool WriteAcademySvg(const FGOContentDefs& Content, const FGOStartScenario& S, unsigned long long Seed, const std::string& Path)
		{
			const FGOHubLayout& Hub = S.Academy;
			const double C = Hub.Params.CellSizeCm;
			const double H = Hub.Params.WallHeightCm;
			const double K = 0.1;	// px per cm
			const double Margin = 30.0;
			const double Top = 50.0;
			const FBox& B = Hub.BoundsCm;
			// Unreal top view: +X (forward) points up the page, +Y (right) to the right.
			auto SX = [&](double Y) { return (Y - B.Min.Y) * K + Margin; };
			auto SY = [&](double X) { return (B.Max.X - X) * K + Margin + Top; };
			const double Width = (B.Max.Y - B.Min.Y) * K + 2 * Margin;
			const double Height = (B.Max.X - B.Min.X) * K + 2 * Margin + Top + 40;

			std::ostringstream Svg;
			Svg << "<svg xmlns='http://www.w3.org/2000/svg' width='" << Width << "' height='" << Height << "' font-family='sans-serif'>\n";
			Svg << "<rect width='100%' height='100%' fill='#0b1020'/>\n";
			Svg << "<text x='" << Margin << "' y='28' fill='#ffd678' font-size='20'>" << Escape(Hub.AcademyName.ToStd())
				<< "</text><text x='" << Margin << "' y='46' fill='#9ab' font-size='12'>seed " << Seed << " - " << Hub.Rooms.Num()
				<< " rooms, " << Hub.Props.Num() << " props, " << Hub.Spawns.Num() << " spawns - 1 grid cell = " << C / 100.0 << " m</text>\n";

			auto Polygon = [&](const FTransform& T, const FVector& Size, const std::string& Fill, const std::string& Extra, const std::string& Title)
			{
				const FVector AX = T.GetRotation().RotateVector(FVector(1.0, 0.0, 0.0)) * (Size.X * 0.5);
				const FVector AY = T.GetRotation().RotateVector(FVector(0.0, 1.0, 0.0)) * (Size.Y * 0.5);
				const FVector P = T.GetLocation();
				const FVector Corners[4] = { P + AX + AY, P + AX - AY, P - AX - AY, P - AX + AY };
				Svg << "<polygon points='";
				for (const FVector& Corner : Corners)
				{
					Svg << SX(Corner.Y) << "," << SY(Corner.X) << " ";
				}
				Svg << "' fill='" << Fill << "' " << Extra;
				if (Title.empty())
				{
					Svg << "/>\n";
				}
				else
				{
					Svg << "><title>" << Escape(Title) << "</title></polygon>\n";
				}
			};

			for (const FGOHubPiece& Piece : Hub.Structure)
			{
				if (Piece.Transform.GetLocation().Z < 0.0)
				{
					Polygon(Piece.Transform, Piece.SlotSizeCm, Hex(Piece.Tint), "opacity='0.55'", "");
				}
			}
			for (const FGOHubPiece& Piece : Hub.Structure)
			{
				const FVector L = Piece.Transform.GetLocation();
				if (FMath::Abs(L.Z - H * 0.5) > 1.0)
				{
					continue;
				}
				const FVector Along = Piece.Transform.GetRotation().RotateVector(FVector(0.0, 1.0, 0.0)) * (C * 0.5);
				const std::string Id = Piece.AssetId.ToString().ToStd();
				auto Line = [&](const FVector& A, const FVector& Bv, const char* Color, double W, const char* Dash)
				{
					Svg << "<line x1='" << SX(A.Y) << "' y1='" << SY(A.X) << "' x2='" << SX(Bv.Y) << "' y2='" << SY(Bv.X)
						<< "' stroke='" << Color << "' stroke-width='" << W << "' " << Dash << "/>\n";
				};
				if (Id == "hub_doorway")
				{
					Line(L - Along, L - Along * 0.35, "#d8dde6", 2.5, "");
					Line(L + Along * 0.35, L + Along, "#d8dde6", 2.5, "");
				}
				else if (Id == "hub_wall_window")
				{
					Line(L - Along, L + Along, "#6cf", 3.0, "");
				}
				else if (Id == "hub_hangar_door")
				{
					Line(L - Along, L + Along, "#fc0", 3.0, "stroke-dasharray='6,4'");
				}
				else
				{
					Line(L - Along, L + Along, "#d8dde6", 2.5, "");
				}
			}
			for (const FGOHubPiece& Piece : Hub.Props)
			{
				if (Piece.Transform.GetLocation().Z > H * 0.5)
				{
					continue;	// ceiling lights
				}
				const FGOAssetKitRow* Row = Content.FindAsset(Piece.AssetId);
				Polygon(Piece.Transform, Piece.SlotSizeCm, Hex(Row ? Row->FallbackColor : Piece.Tint),
					"stroke='#0b1020' stroke-width='0.6'", Piece.AssetId.ToString().ToStd());
			}
			for (const FGOHubSpawnPoint& Spawn : Hub.Spawns)
			{
				const FVector P = Spawn.Transform.GetLocation();
				const char* Fill = "#39f";
				double R = 3.0;
				switch (Spawn.Role)
				{
				case EGOHubSpawnRole::Player:		Fill = "#ffd400"; R = 8.0; break;
				case EGOHubSpawnRole::Instructor:	Fill = "#ff5a5a"; R = 4.0; break;
				case EGOHubSpawnRole::Staff:		Fill = "#4fd07a"; R = 4.0; break;
				case EGOHubSpawnRole::Ship:			continue;
				default: break;
				}
				const FVector Facing = Spawn.Transform.GetRotation().RotateVector(FVector(1.0, 0.0, 0.0)) * (R * 1.8 / K);
				Svg << "<line x1='" << SX(P.Y) << "' y1='" << SY(P.X) << "' x2='" << SX(P.Y + Facing.Y) << "' y2='" << SY(P.X + Facing.X)
					<< "' stroke='" << Fill << "' stroke-width='1.2'/>";
				Svg << "<circle cx='" << SX(P.Y) << "' cy='" << SY(P.X) << "' r='" << R << "' fill='" << Fill
					<< "' stroke='#000' stroke-width='0.8'><title>" << Escape(Spawn.DisplayName.ToStd()) << "</title></circle>\n";
			}
			for (const FGOHubRoom& Room : Hub.Rooms)
			{
				Svg << "<text x='" << SX(Room.CenterCm.Y) << "' y='" << SY(Room.CenterCm.X) - 8
					<< "' fill='#fff' font-size='13' text-anchor='middle' stroke='#0b1020' stroke-width='3' paint-order='stroke'>"
					<< Escape(Room.DisplayName.ToStd()) << "</text>\n";
			}
			const double LegendY = Height - 18;
			Svg << "<g font-size='12' fill='#cde'>"
				<< "<circle cx='" << Margin << "' cy='" << LegendY << "' r='6' fill='#ffd400'/><text x='" << Margin + 12 << "' y='" << LegendY + 4 << "'>player</text>"
				<< "<circle cx='" << Margin + 80 << "' cy='" << LegendY << "' r='4' fill='#39f'/><text x='" << Margin + 90 << "' y='" << LegendY + 4 << "'>cadet</text>"
				<< "<circle cx='" << Margin + 150 << "' cy='" << LegendY << "' r='4' fill='#ff5a5a'/><text x='" << Margin + 160 << "' y='" << LegendY + 4 << "'>instructor</text>"
				<< "<circle cx='" << Margin + 245 << "' cy='" << LegendY << "' r='4' fill='#4fd07a'/><text x='" << Margin + 255 << "' y='" << LegendY + 4 << "'>staff</text>"
				<< "<line x1='" << Margin + 310 << "' y1='" << LegendY << "' x2='" << Margin + 340 << "' y2='" << LegendY << "' stroke='#6cf' stroke-width='3'/><text x='" << Margin + 346 << "' y='" << LegendY + 4 << "'>window</text>"
				<< "<line x1='" << Margin + 410 << "' y1='" << LegendY << "' x2='" << Margin + 440 << "' y2='" << LegendY << "' stroke='#fc0' stroke-width='3' stroke-dasharray='6,4'/><text x='" << Margin + 446 << "' y='" << LegendY + 4 << "'>hangar door</text>"
				<< "</g>\n";
			Svg << "</svg>\n";

			std::ofstream File(Path);
			File << Svg.str();
			return static_cast<bool>(File);
		}

		// ---- Home world map ----
		bool WritePlanetPng(const FGOContentDefs& Content, const FGOStartScenario& S, const std::string& Path)
		{
			const FGOPlanetTerrain Terrain(S.Universe.GetHomePlanet(), Content, S.TerrainHeightScale);
			const int Width = 800;
			const int Height = 400;
			const int Lod = 7;
			const double Pi = UE_DOUBLE_PI;
			std::vector<unsigned char> Rgb(static_cast<size_t>(Width) * Height * 3);

			auto Direction = [](double Lat, double Lon) { return FVector(FMath::Cos(Lat) * FMath::Cos(Lon), FMath::Cos(Lat) * FMath::Sin(Lon), FMath::Sin(Lat)); };
			const double PixelAngle = 2.0 * Pi / Width;
			for (int Y = 0; Y < Height; ++Y)
			{
				const double Lat = (0.5 - (Y + 0.5) / Height) * Pi;
				for (int X = 0; X < Width; ++X)
				{
					const double Lon = (X + 0.5) * PixelAngle - Pi;
					const FGOTerrainSample Sample = Terrain.Sample(Direction(Lat, Lon), Lod);
					FLinearColor Color = Terrain.BiomeColor(Sample.Biome);
					if (!Sample.bWater)
					{
						// Hillshade from the north-west.
						const double HN = Terrain.Sample(Direction(Lat + PixelAngle, Lon), Lod).Height;
						const double HW = Terrain.Sample(Direction(Lat, Lon - PixelAngle), Lod).Height;
						const double PixelKm = Terrain.GetRadiusKm() * PixelAngle;
						const double Slope = (Terrain.HeightToKm(Sample.Height - HN) + Terrain.HeightToKm(Sample.Height - HW)) / PixelKm;
						const float Shade = static_cast<float>(FMath::Clamp(1.0 + Slope * 12.0, 0.55, 1.45));
						Color = FLinearColor(Color.R * Shade, Color.G * Shade, Color.B * Shade);
					}
					unsigned char* P = &Rgb[(static_cast<size_t>(Y) * Width + X) * 3];
					P[0] = static_cast<unsigned char>(FMath::Clamp(Color.R, 0.f, 1.f) * 255.f);
					P[1] = static_cast<unsigned char>(FMath::Clamp(Color.G, 0.f, 1.f) * 255.f);
					P[2] = static_cast<unsigned char>(FMath::Clamp(Color.B, 0.f, 1.f) * 255.f);
				}
			}

			// Landing site: red cross.
			const double SiteLat = GOCubeSphere::Latitude(S.LandingDirection);
			const double SiteLon = GOCubeSphere::Longitude(S.LandingDirection);
			const int CX = static_cast<int>((SiteLon + Pi) / PixelAngle);
			const int CY = static_cast<int>((0.5 - SiteLat / Pi) * Height);
			for (int D = -9; D <= 9; ++D)
			{
				for (int T = -1; T <= 1; ++T)
				{
					const int Points[2][2] = { { CX + D, CY + T }, { CX + T, CY + D } };
					for (const auto& Pt : Points)
					{
						if (Pt[0] >= 0 && Pt[0] < Width && Pt[1] >= 0 && Pt[1] < Height)
						{
							unsigned char* P = &Rgb[(static_cast<size_t>(Pt[1]) * Width + Pt[0]) * 3];
							P[0] = 255; P[1] = 30; P[2] = 30;
						}
					}
				}
			}
			return WritePng(Path, Width, Height, Rgb);
		}

		// ---- Galaxy ----
		bool WriteGalaxySvg(const FGOContentDefs& Content, const FGOStartScenario& S, const std::string& Path)
		{
			const FGOGalaxyGenerator Galaxy(GOSeed::FromBlueprint(S.Universe.GalaxySeed), S.Universe.GalaxyConfig);
			const FGOStarSystemGenerator StarGen(GOSeed::FromBlueprint(S.Universe.GalaxySeed), Content);
			const double R = Galaxy.GetConfig().RadiusLY;
			const double Size = 900.0;
			const double K = (Size * 0.5 - 20.0) / R;
			auto SX = [&](double Y) { return Size * 0.5 + Y * K; };
			auto SY = [&](double X) { return Size * 0.5 - X * K; };

			TArray<FGOStarRecord> Stars;
			Galaxy.GatherStars(FVector::ZeroVector, R * 1.01, Stars);

			std::ostringstream Svg;
			Svg << "<svg xmlns='http://www.w3.org/2000/svg' width='" << Size << "' height='" << Size + 30 << "' font-family='sans-serif'>\n";
			Svg << "<rect width='100%' height='100%' fill='#03040a'/>\n";
			Svg << "<text x='16' y='24' fill='#ffd678' font-size='18'>" << Escape(S.Universe.GalaxyName.ToStd()) << "</text>"
				<< "<text x='16' y='42' fill='#89a' font-size='12'>" << Stars.Num() << " systems, radius " << static_cast<int>(R) << " ly</text>\n";
			for (const double Band : { 0.1, 0.4, 1.0 })
			{
				Svg << "<circle cx='" << SX(0) << "' cy='" << SY(0) << "' r='" << Band * R * K
					<< "' fill='none' stroke='#2a3550' stroke-dasharray='4,6'/>\n";
			}
			for (const FGOStarRecord& Star : Stars)
			{
				const FGOStarSystem System = StarGen.Generate(Star, Galaxy.ClassifyRegion(Star.PositionLY));
				const double Radius = System.SpectralClass <= EGOSpectralClass::B ? 2.2 : System.SpectralClass == EGOSpectralClass::M ? 1.0 : 1.5;
				Svg << "<circle cx='" << SX(Star.PositionLY.Y) << "' cy='" << SY(Star.PositionLY.X) << "' r='" << Radius
					<< "' fill='" << Hex(System.Color) << "'/>\n";
			}
			const FVector Home = S.Universe.HomeStar.PositionLY;
			Svg << "<circle cx='" << SX(Home.Y) << "' cy='" << SY(Home.X) << "' r='9' fill='none' stroke='#ffd400' stroke-width='2'/>"
				<< "<text x='" << SX(Home.Y) + 12 << "' y='" << SY(Home.X) + 4 << "' fill='#ffd400' font-size='13'>"
				<< Escape(S.Universe.HomeSystem.Name.ToStd()) << " (home)</text>\n";
			Svg << "</svg>\n";

			std::ofstream File(Path);
			File << Svg.str();
			return static_cast<bool>(File);
		}
	}

	bool WritePreviews(const FGOContentDefs& Content, unsigned long long Seed, const std::string& OutDir)
	{
		FGOStartScenarioParams Params;
		Params.Universe.Seed = Seed;
		const FGOStartScenario Scenario = GOStartScenario::Generate(Content, Params);
		if (!Scenario.IsValid())
		{
			std::cout << "Seed " << Seed << " produced no scenario\n";
			return false;
		}
		for (const FString& Line : Scenario.Log)
		{
			std::cout << *Line << "\n";
		}
		const std::string Suffix = std::to_string(Seed);
		bool bOk = WriteAcademySvg(Content, Scenario, Seed, OutDir + "/academy_" + Suffix + ".svg");
		bOk &= WritePlanetPng(Content, Scenario, OutDir + "/planet_" + Suffix + ".png");
		bOk &= WriteGalaxySvg(Content, Scenario, OutDir + "/galaxy_" + Suffix + ".svg");
		std::cout << (bOk ? "Wrote previews to " : "Failed writing previews to ") << OutDir << "\n";
		return bOk;
	}
}
