/**
 * Reproducible design calculations, not a gameplay allocator.
 * Models 100 independent uniform points in a disk, and enumerates a proposed
 * chunk-aligned reservation grid. Run with: node docs/analysis/start-point-analysis.mjs
 */

const playerCount = 100;
const radius = 1_000_000;
const distances = [1_024, 4_096, 8_192, 16_384, 32_768];
const pairs = playerCount * (playerCount - 1) / 2;

// The disk-overlap integral gives the exact pair-distance CDF, up to quadrature error.
function pairProbability(distance, diskRadius) {
    const limit = Math.min(distance / diskRadius, 2);
    const steps = 4096;
    const step = limit / steps;
    const density = value => 4 * value / Math.PI * (
        Math.acos(value / 2) - value / 2 * Math.sqrt(Math.max(0, 1 - value * value / 4))
    );
    let sum = density(0) + density(limit);
    for (let index = 1; index < steps; index++) {
        sum += (index % 2 === 0 ? 2 : 4) * density(index * step);
    }
    return sum * step / 3;
}

// Independent simulation checks the probability of ANY close pair, not just one pair.
// Fixed seed makes the numerical evidence reproducible; this PRNG is not proposed for UUID hashing.
let randomState = 0x4b1d2026;
function random() {
    randomState ^= randomState << 13;
    randomState ^= randomState >>> 17;
    randomState ^= randomState << 5;
    return (randomState >>> 0) / 4294967296;
}

const trials = 20_000;
const closeTrials = distances.map(() => 0);
for (let trial = 0; trial < trials; trial++) {
    const points = Array.from({length: playerCount}, () => {
        const radial = radius * Math.sqrt(random());
        const angle = 2 * Math.PI * random();
        return [radial * Math.cos(angle), radial * Math.sin(angle)];
    });
    let minimumSquared = Infinity;
    for (let first = 0; first < playerCount; first++) {
        for (let second = 0; second < first; second++) {
            const dx = points[first][0] - points[second][0];
            const dz = points[first][1] - points[second][1];
            minimumSquared = Math.min(minimumSquared, dx * dx + dz * dz);
        }
    }
    distances.forEach((distance, index) => {
        if (minimumSquared < distance * distance) {
            closeTrials[index]++;
        }
    });
}

function gridCapacity(diskRadius, spacing) {
    const limit = Math.ceil(diskRadius / spacing);
    let slots = 0;
    let maximumAbsoluteCoordinate = 0;
    for (let x = -limit; x <= limit; x++) {
        for (let z = -limit; z <= limit; z++) {
            // Fixed +8 offset puts every candidate on the same location within a chunk.
            const blockX = x * spacing + 8;
            const blockZ = z * spacing + 8;
            if (blockX * blockX + blockZ * blockZ <= diskRadius * diskRadius) {
                slots++;
                maximumAbsoluteCoordinate = Math.max(maximumAbsoluteCoordinate, Math.abs(blockX), Math.abs(blockZ));
            }
        }
    }
    return {radius: diskRadius, spacing, slots, maximumAbsoluteCoordinate};
}

const results = distances.map((distance, index) => {
    const pair = pairProbability(distance, radius);
    const expectedClosePairs = pairs * pair;
    const simulation = closeTrials[index] / trials;
    return {
        distance,
        pairProbability: pair,
        expectedClosePairs,
        poissonAnyPairApproximation: -Math.expm1(-expectedClosePairs),
        rigorousUnionUpperBound: Math.min(1, expectedClosePairs),
        simulatedAnyPairProbability: simulation,
        simulationStandardError: Math.sqrt(simulation * (1 - simulation) / trials),
        // Since pair probability <= (distance / radius)^2, this radius suffices by the union bound.
        radiusForUnionBoundAtMostOneInMillion: distance * Math.sqrt(pairs / 1e-6)
    };
});

console.log(JSON.stringify({
    assumptions: {playerCount, radius, pairs, trials, seed: '0x4b1d2026', distribution: 'independent uniform disk'},
    results,
    grids: [65_536, 131_072, 262_144].flatMap(gridRadius =>
        [8_192, 16_384, 32_768].map(spacing => gridCapacity(gridRadius, spacing))),
    rawCoordinatePrecision: [65_536, 131_072, 262_144, 1_000_000].map(coordinate => ({
        coordinate,
        floatUlp: 2 ** (Math.floor(Math.log2(coordinate)) - 23),
        doubleUlp: 2 ** (Math.floor(Math.log2(coordinate)) - 52)
    })),
    checks: {
        fullDiskPairProbability: pairProbability(2, 1),
        zeroDistancePairProbability: pairProbability(0, 1)
    }
}, null, 2));
