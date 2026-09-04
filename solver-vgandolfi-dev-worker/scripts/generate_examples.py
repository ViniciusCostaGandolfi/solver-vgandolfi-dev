#!/usr/bin/env python3
"""
Gerador de exemplos de TSP e VRP para os assets do frontend.

Usa os SOLVERS REAIS do worker (TspLkhResolver / VrpSolver) para produzir
soluções válidas e salva os JSONs na mesma estrutura que o frontend espera
(app/assets/examples/): TSP -> optimized_stops/route_line/distance_meters/
time_to_solve_ms; VRP -> id/origin/routes[].route_line/clients/.../time_to_solve_ms.

Como rodar (na raiz do solver-vgandolfi-dev-worker, com o venv do worker ativo):
    python scripts/generate_examples.py            # gera tudo (sobrescreve tsp-1/2/3 e vrp-1/2/3)
    python scripts/generate_examples.py --tsp      # só TSP
    python scripts/generate_examples.py --vrp      # só VRP
    python scripts/generate_examples.py --out /tmp/exemplos   # outro destino

Os exemplos usam SP_OUTER_LANDMARKS (50 pontos únicos espalhados pela Grande
SP, média ~25 km do centro) — sem pontos sobrepostos e com rotas bem
dispersas. O script SOBRESCREVE os arquivos tsp-1/2/3.json e vrp-1/2/3.json.

Ajuste as constantes abaixo (TSP_SIZES, VRP_CONFIGS, OUTPUT_DIR, SOLVE_*).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import uuid
from pathlib import Path

# Garante que a raiz do worker esteja no sys.path (import app.*) mesmo
# rodando `python3 scripts/generate_examples.py` de fora da raiz.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# --- Configuração (ajuste à vontade) --------------------------------------

# Número de paradas de cada exemplo TSP (10, 20 e 30).
TSP_SIZES: list[int] = [10, 20, 30]

# Exemplos VRP: cada item = (qtde de clientes, qtde de veículos).
# maxDeliveries por veículo = ceil(clientes/veículos), forçando as rotas pedidas:
# 20/2 -> 2 rotas · 30/3 -> 3 rotas · 50/5 -> 5 rotas.
VRP_CONFIGS: list[tuple[int, int]] = [
    (20, 2),
    (30, 3),
    (50, 5),
]

# Para gerar exemplos com >= 3 rotas garantido, use 3+ veículos.
MIN_ROUTES_PER_VRP = 3

# Diretório de saída (default: assets do frontend).
# Calculado de forma relativa à raiz do worker, para funcionar em qualquer
# máquina (sem caminho absoluto hardcoded).
WORKER_ROOT = Path(__file__).resolve().parent.parent
WEB_ASSETS_DIR = WORKER_ROOT.parent / "solver-vgandolfi-dev-web" / "app" / "assets" / "examples"
OUTPUT_DIR = WEB_ASSETS_DIR

# Tempo máximo (s) que o VRP solver usa por exemplo. OR-Tools usa o time limit
# inteiro mesmo em instâncias pequenas; reduza para gerar mais rápido.
SOLVE_VRP_TIMEOUT_SECONDS = 30

# Solver TSP: True usa OSRM (matrix STREET); False usa haversine (EUCLIDIAN).
# Sem rede/OSRM, mantenha False (funciona offline).
TSP_USE_STREET = False

# ---------------------------------------------------------------------------

# Pontos espalhados da Grande SP (nome, lat, lng) — BEM mais afastados do
# centro (média ~25 km da origem/MASP vs ~5,8 km dos landmarks antigos) e com
# 50 pontos únicos (nenhum par praticamente sobreposto; mínimo ~2 km entre si).
# Usados em TODOS os exemplos TSP (10/20/30) e VRP (20/30/50 clientes), para
# as rotas demonstrarem dispersão real pela região metropolitana.
SP_OUTER_LANDMARKS: list[tuple[str, float, float]] = [
    # Norte
    ("Caieiras", -23.3645, -46.7432),
    ("Franco da Rocha", -23.319, -46.727),
    ("Mairiporã", -23.3193, -46.5869),
    ("Perus", -23.4033, -46.7489),
    ("Tremembé", -23.404, -46.6065),
    ("Freguesia do Ó", -23.4875, -46.6945),
    # Oeste
    ("Osasco Centro", -23.5325, -46.7915),
    ("Carapicuíba", -23.5232, -46.835),
    ("Alphaville", -23.4971, -46.8536),
    ("Barueri", -23.5111, -46.8763),
    ("Jandira", -23.5283, -46.9024),
    ("Itapevi", -23.5488, -46.9343),
    ("Santana de Parnaíba", -23.444, -46.9179),
    ("Cotia", -23.6038, -46.9194),
    ("Vargem Grande Paulista", -23.6022, -47.0231),
    # Leste
    ("Guarulhos Centro", -23.4628, -46.5334),
    ("Aeroporto GRU", -23.4356, -46.4731),
    ("Arujá", -23.3968, -46.3221),
    ("Itaquaquecetuba", -23.49, -46.345),
    ("São Miguel Paulista", -23.4935, -46.446),
    ("Itaim Paulista", -23.492, -46.401),
    ("Penha", -23.5245, -46.5413),
    ("Itaquera", -23.541, -46.458),
    ("Guaianases", -23.5408, -46.411),
    ("Cidade Tiradentes", -23.597, -46.388),
    ("Poá", -23.528, -46.345),
    ("Ferraz de Vasconcelos", -23.54, -46.37),
    ("Suzano", -23.5427, -46.3109),
    ("Mogi das Cruzes", -23.5226, -46.1885),
    ("Biritiba Mirim", -23.57, -46.04),
    # Sudeste / ABC
    ("São Bernardo do Campo", -23.6943, -46.5654),
    ("Santo André", -23.6639, -46.5383),
    ("São Caetano do Sul", -23.6228, -46.565),
    ("Diadema", -23.6864, -46.6227),
    ("Mauá", -23.6682, -46.4612),
    ("Ribeirão Pires", -23.713, -46.4135),
    ("Rio Grande da Serra", -23.7442, -46.3977),
    # Sul
    ("Cidade Dutra", -23.705, -46.692),
    ("Grajaú", -23.76, -46.681),
    ("Jardim Ângela", -23.7006, -46.7628),
    ("Capela do Socorro", -23.7911, -46.7137),
    ("Parelheiros", -23.8333, -46.7),
    ("Embu-Guaçu", -23.8325, -46.8117),
    ("Itapecerica da Serra", -23.7172, -46.8492),
    ("Represa Guarapiranga", -23.7226, -46.73),
    # Sudoeste
    ("Taboão da Serra", -23.6019, -46.7526),
    ("Embu das Artes", -23.6489, -46.8521),
    ("São Lourenço da Serra", -23.8538, -46.9436),
    # Noroeste
    ("Jundiaí", -23.1865, -46.8842),
    ("Várzea Paulista", -23.2109, -46.8275),
]

# Origem dos exemplos: Av. Paulista / MASP.
ORIGIN = {"lat": -23.561399, "lng": -46.655794}


def resolve_tsp(n_stops: int) -> dict:
    """Resolve um TSP com n_stops usando o solver real e devolve dict pronto p/ JSON."""
    from app.algorithms.tsp.lkh_solver import TspLkhResolver
    from app.config import Settings
    from app.dtos import Address, Coordinate, MatrixType, TspRequest

    stops = []
    for i in range(n_stops):
        name, lat, lng = SP_OUTER_LANDMARKS[i % len(SP_OUTER_LANDMARKS)]
        stops.append(
            {
                "id": f"s{i+1}",
                "name": name,
                "customer_name": name,
                "address": Address(
                    customer_name=name,
                    street_name="",
                    street_number="",
                    city="São Paulo",
                    state="SP",
                    postal_code="",
                    latitude=lat,
                    longitude=lng,
                ),
            }
        )

    request = TspRequest(
        origin=Address(
            customer_name="MASP",
            street_name="",
            street_number="",
            city="São Paulo",
            state="SP",
            postal_code="",
            latitude=ORIGIN["lat"],
            longitude=ORIGIN["lng"],
        ),
        stops=stops,
        matrix_type=MatrixType.STREET if TSP_USE_STREET else MatrixType.EUCLIDIAN,
    )
    settings = Settings()
    result = TspLkhResolver(request, settings).resolve()
    return result.model_dump(mode="json")


def resolve_vrp(
    n_clients: int,
    n_vehicles: int,
    landmarks: list[tuple[str, float, float]] | None = None,
) -> dict:
    """Resolve um VRP com n_clients/n_vehicles e devolve dict pronto p/ JSON.

    landmarks: lista de (nome, lat, lng) usada para sortear os clientes
    (default: SP_OUTER_LANDMARKS — pontos espalhados da Grande SP).
    """
    from app.algorithms.vrp.vrp_solver import VrpSolver
    from app.config import Settings
    from app.dtos import Address, MatrixType, VehicleType, VrpIn

    landmarks = landmarks if landmarks is not None else SP_OUTER_LANDMARKS

    # Clientes espalhados nos landmarks
    clients = []
    for i in range(n_clients):
        name, lat, lng = landmarks[i % len(landmarks)]
        clients.append(
            {
                "id": str(uuid.uuid4()),
                "name": name,
                "customer_name": name,
                "volume_liters": 8.0 + (i * 3) % 20,
                "weight_kg": 10.0 + (i * 7) % 50,
                "created_at": 0,
                "address": Address(
                    customer_name=name,
                    street_name="",
                    street_number="",
                    city="São Paulo",
                    state="SP",
                    postal_code="",
                    latitude=lat,
                    longitude=lng,
                ),
            }
        )

    # Veículos com maxDeliveries que força divisão em >= 3 rotas:
    # capacidade total de entregas = ceil(n/veículos) * veículos >= n.
    import math

    per_vehicle = math.ceil(n_clients / n_vehicles)
    vehicles = []
    for i in range(n_vehicles):
        vehicles.append(
            VehicleType(
                id=str(uuid.uuid4()),
                name=f"Van {i+1}",
                max_volume_liters=float(per_vehicle * 60),
                max_weight_kg=float(per_vehicle * 120),
                max_deliveries=per_vehicle,
            )
        )

    vrp_in = VrpIn(
        origin=Address(
            customer_name="MASP",
            street_name="",
            street_number="",
            city="São Paulo",
            state="SP",
            postal_code="",
            latitude=ORIGIN["lat"],
            longitude=ORIGIN["lng"],
        ),
        clients=clients,
        vehicles=vehicles,
        matrix_type=MatrixType.EUCLIDIAN,
    )
    settings = Settings(VRP_TIMEOUT_SECONDS=SOLVE_VRP_TIMEOUT_SECONDS)
    result = VrpSolver(vrp_in, settings).resolve()
    data = result.model_dump(mode="json")

    # Garantia de >= 3 rotas
    n_routes = len(data.get("routes", []))
    if n_routes < MIN_ROUTES_PER_VRP:
        print(
            f"  [aviso] VRP com {n_clients} clientes / {n_vehicles} veículos "
            f"gerou apenas {n_routes} rota(s) — aumente a frota ou o nº de clientes."
        )
    return data


def save(data: dict, filename: str, meta: dict) -> Path:
    data = dict(data)
    data["_meta"] = meta
    out = OUTPUT_DIR / filename
    out.write_text(json.dumps(data, ensure_ascii=False, indent=2))
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description="Gera exemplos TSP/VRP para os assets do frontend")
    parser.add_argument("--tsp", action="store_true", help="gera só TSP")
    parser.add_argument("--vrp", action="store_true", help="gera só VRP")
    parser.add_argument("--out", type=str, default=None, help="diretório de saída")
    args = parser.parse_args()

    global OUTPUT_DIR
    if args.out:
        OUTPUT_DIR = Path(args.out)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    do_tsp = args.tsp or not args.vrp
    do_vrp = args.vrp or not args.tsp

    print(f"Saída: {OUTPUT_DIR}")
    print(f"Solvers: TSP(LKH, street={TSP_USE_STREET}) / VRP(OR-Tools, timeout={SOLVE_VRP_TIMEOUT_SECONDS}s)")

    if do_tsp:
        print("\n=== TSP ===")
        for i, n in enumerate(TSP_SIZES, 1):
            t0 = time.time()
            data = resolve_tsp(n)
            path = save(data, f"tsp-{i}.json", {"id": str(uuid.uuid4()), "n_stops": n, "name": f"tsp-{i}.json"})
            print(f"  tsp-{i}.json: {n} paradas, {data['distance_meters']:.0f} m, "
                  f"{(time.time()-t0):.1f}s -> {path.name}")

    if do_vrp:
        print("\n=== VRP ===")
        for i, (n, v) in enumerate(VRP_CONFIGS, 1):
            t0 = time.time()
            data = resolve_vrp(n, v)
            n_routes = len(data.get("routes", []))
            path = save(data, f"vrp-{i}.json", {"id": data.get("id"), "n_clients": n, "n_routes": n_routes, "name": f"vrp-{i}.json"})
            print(f"  vrp-{i}.json: {n} clientes / {v} veículos, {n_routes} rotas, "
                  f"{(time.time()-t0):.1f}s -> {path.name}")

    print("\nOK.")


if __name__ == "__main__":
    main()