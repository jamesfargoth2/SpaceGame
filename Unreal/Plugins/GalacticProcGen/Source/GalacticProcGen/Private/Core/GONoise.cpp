#include "Core/GONoise.h"
#include "Core/GORandom.h"

namespace GONoisePrivate
{
	const float F2 = 0.5f * (1.7320508075688772f - 1.0f);
	const float G2 = (3.0f - 1.7320508075688772f) / 6.0f;
	const float F3 = 1.0f / 3.0f;
	const float G3 = 1.0f / 6.0f;

	const int8 Grad2[8][2] = {
		{1, 1}, {-1, 1}, {1, -1}, {-1, -1},
		{1, 0}, {-1, 0}, {0, 1}, {0, -1}
	};

	const int8 Grad3[12][3] = {
		{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
		{1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
		{0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
	};

	FORCEINLINE int32 FastFloor(float X)
	{
		const int32 Xi = static_cast<int32>(X);
		return X < static_cast<float>(Xi) ? Xi - 1 : Xi;
	}
}

FGOSimplexNoise::FGOSimplexNoise(uint64 Seed)
{
	uint8 Base[256];
	for (int32 I = 0; I < 256; ++I)
	{
		Base[I] = static_cast<uint8>(I);
	}

	FGORandom Rng(Seed);
	for (int32 I = 255; I > 0; --I)
	{
		const int32 J = Rng.RandHelper(I + 1);
		const uint8 Tmp = Base[I];
		Base[I] = Base[J];
		Base[J] = Tmp;
	}

	for (int32 I = 0; I < 512; ++I)
	{
		Perm[I] = Base[I & 255];
	}
}

float FGOSimplexNoise::Noise2(float X, float Y) const
{
	const float S = (X + Y) * GONoisePrivate::F2;
	const int32 I = GONoisePrivate::FastFloor(X + S);
	const int32 J = GONoisePrivate::FastFloor(Y + S);

	const float T = static_cast<float>(I + J) * GONoisePrivate::G2;
	const float X0 = X - (static_cast<float>(I) - T);
	const float Y0 = Y - (static_cast<float>(J) - T);

	const int32 I1 = X0 > Y0 ? 1 : 0;
	const int32 J1 = X0 > Y0 ? 0 : 1;

	const float X1 = X0 - static_cast<float>(I1) + GONoisePrivate::G2;
	const float Y1 = Y0 - static_cast<float>(J1) + GONoisePrivate::G2;
	const float X2 = X0 - 1.f + 2.f * GONoisePrivate::G2;
	const float Y2 = Y0 - 1.f + 2.f * GONoisePrivate::G2;

	const int32 Ii = I & 255;
	const int32 Jj = J & 255;

	auto Contribution = [this](float Cx, float Cy, int32 Gi, int32 Gj)
	{
		float Tc = 0.5f - Cx * Cx - Cy * Cy;
		if (Tc < 0.f)
		{
			return 0.f;
		}
		Tc *= Tc;
		const int8* G = GONoisePrivate::Grad2[Perm[(Gi + Perm[Gj & 255]) & 511] & 7];
		return Tc * Tc * (G[0] * Cx + G[1] * Cy);
	};

	const float N0 = Contribution(X0, Y0, Ii, Jj);
	const float N1 = Contribution(X1, Y1, Ii + I1, Jj + J1);
	const float N2 = Contribution(X2, Y2, Ii + 1, Jj + 1);
	return 70.f * (N0 + N1 + N2);
}

float FGOSimplexNoise::Noise3(float X, float Y, float Z) const
{
	const float S = (X + Y + Z) * GONoisePrivate::F3;
	const int32 I = GONoisePrivate::FastFloor(X + S);
	const int32 J = GONoisePrivate::FastFloor(Y + S);
	const int32 K = GONoisePrivate::FastFloor(Z + S);

	const float T = static_cast<float>(I + J + K) * GONoisePrivate::G3;
	const float X0 = X - (static_cast<float>(I) - T);
	const float Y0 = Y - (static_cast<float>(J) - T);
	const float Z0 = Z - (static_cast<float>(K) - T);

	int32 I1, J1, K1, I2, J2, K2;
	if (X0 >= Y0)
	{
		if (Y0 >= Z0)      { I1 = 1; J1 = 0; K1 = 0; I2 = 1; J2 = 1; K2 = 0; }
		else if (X0 >= Z0) { I1 = 1; J1 = 0; K1 = 0; I2 = 1; J2 = 0; K2 = 1; }
		else               { I1 = 0; J1 = 0; K1 = 1; I2 = 1; J2 = 0; K2 = 1; }
	}
	else
	{
		if (Y0 < Z0)       { I1 = 0; J1 = 0; K1 = 1; I2 = 0; J2 = 1; K2 = 1; }
		else if (X0 < Z0)  { I1 = 0; J1 = 1; K1 = 0; I2 = 0; J2 = 1; K2 = 1; }
		else               { I1 = 0; J1 = 1; K1 = 0; I2 = 1; J2 = 1; K2 = 0; }
	}

	const float X1 = X0 - static_cast<float>(I1) + GONoisePrivate::G3;
	const float Y1 = Y0 - static_cast<float>(J1) + GONoisePrivate::G3;
	const float Z1 = Z0 - static_cast<float>(K1) + GONoisePrivate::G3;
	const float X2 = X0 - static_cast<float>(I2) + 2.f * GONoisePrivate::G3;
	const float Y2 = Y0 - static_cast<float>(J2) + 2.f * GONoisePrivate::G3;
	const float Z2 = Z0 - static_cast<float>(K2) + 2.f * GONoisePrivate::G3;
	const float X3 = X0 - 1.f + 3.f * GONoisePrivate::G3;
	const float Y3 = Y0 - 1.f + 3.f * GONoisePrivate::G3;
	const float Z3 = Z0 - 1.f + 3.f * GONoisePrivate::G3;

	const int32 Ii = I & 255;
	const int32 Jj = J & 255;
	const int32 Kk = K & 255;

	auto Contribution = [this](float Cx, float Cy, float Cz, int32 Gi, int32 Gj, int32 Gk)
	{
		float Tc = 0.6f - Cx * Cx - Cy * Cy - Cz * Cz;
		if (Tc < 0.f)
		{
			return 0.f;
		}
		Tc *= Tc;
		const int8* G = GONoisePrivate::Grad3[Perm[(Gi + Perm[(Gj + Perm[Gk & 255]) & 255]) & 511] % 12];
		return Tc * Tc * (G[0] * Cx + G[1] * Cy + G[2] * Cz);
	};

	const float N0 = Contribution(X0, Y0, Z0, Ii, Jj, Kk);
	const float N1 = Contribution(X1, Y1, Z1, Ii + I1, Jj + J1, Kk + K1);
	const float N2 = Contribution(X2, Y2, Z2, Ii + I2, Jj + J2, Kk + K2);
	const float N3 = Contribution(X3, Y3, Z3, Ii + 1, Jj + 1, Kk + 1);
	return 32.f * (N0 + N1 + N2 + N3);
}

float FGONoise::Fbm2(float X, float Y, int32 Octaves, float Persistence, float Lacunarity) const
{
	float Value = 0.f;
	float Amplitude = 1.f;
	float Frequency = 1.f;
	float MaxValue = 0.f;
	for (int32 I = 0; I < Octaves; ++I)
	{
		Value += Simplex.Noise2(X * Frequency, Y * Frequency) * Amplitude;
		MaxValue += Amplitude;
		Amplitude *= Persistence;
		Frequency *= Lacunarity;
	}
	return MaxValue > 0.f ? Value / MaxValue : 0.f;
}

float FGONoise::Fbm3(float X, float Y, float Z, int32 Octaves, float Persistence, float Lacunarity) const
{
	float Value = 0.f;
	float Amplitude = 1.f;
	float Frequency = 1.f;
	float MaxValue = 0.f;
	for (int32 I = 0; I < Octaves; ++I)
	{
		Value += Simplex.Noise3(X * Frequency, Y * Frequency, Z * Frequency) * Amplitude;
		MaxValue += Amplitude;
		Amplitude *= Persistence;
		Frequency *= Lacunarity;
	}
	return MaxValue > 0.f ? Value / MaxValue : 0.f;
}

float FGONoise::RidgedFbm3(float X, float Y, float Z, int32 Octaves, float Lacunarity, float Gain) const
{
	float Value = 0.f;
	float Amplitude = 1.f;
	float Frequency = 1.f;
	float Weight = 1.f;
	for (int32 I = 0; I < Octaves; ++I)
	{
		float Signal = Simplex.Noise3(X * Frequency, Y * Frequency, Z * Frequency);
		Signal = 1.f - FMath::Abs(Signal);
		Signal *= Signal;
		Signal *= Weight;
		Weight = FMath::Clamp(Signal * Gain, 0.f, 1.f);
		Value += Signal * Amplitude;
		Amplitude *= 0.5f;
		Frequency *= Lacunarity;
	}
	return Value;
}

float FGONoise::BillowFbm3(float X, float Y, float Z, int32 Octaves, float Persistence, float Lacunarity) const
{
	float Value = 0.f;
	float Amplitude = 1.f;
	float Frequency = 1.f;
	float MaxValue = 0.f;
	for (int32 I = 0; I < Octaves; ++I)
	{
		const float Signal = FMath::Abs(Simplex.Noise3(X * Frequency, Y * Frequency, Z * Frequency)) * 2.f - 1.f;
		Value += Signal * Amplitude;
		MaxValue += Amplitude;
		Amplitude *= Persistence;
		Frequency *= Lacunarity;
	}
	return MaxValue > 0.f ? Value / MaxValue : 0.f;
}

float FGONoise::DomainWarp3(float X, float Y, float Z, float WarpStrength, int32 WarpOctaves, int32 MainOctaves) const
{
	const float Wx = Fbm3(X + 1.7f, Y + 9.2f, Z + 3.1f, WarpOctaves);
	const float Wy = Fbm3(X + 8.3f, Y + 2.8f, Z + 7.4f, WarpOctaves);
	const float Wz = Fbm3(X + 4.1f, Y + 6.5f, Z + 5.9f, WarpOctaves);
	return Fbm3(X + WarpStrength * Wx, Y + WarpStrength * Wy, Z + WarpStrength * Wz, MainOctaves);
}
