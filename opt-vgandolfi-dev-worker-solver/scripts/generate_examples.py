#!/usr/bin/env python3
"""
Gerador de exemplos de TSP e VRP para os assets do frontend.

Usa os SOLVERS REAIS do worker (TspLkhResolver / VrpSolver) para produzir
soluções válidas e salva os JSONs na mesma estrutura que o frontend espera
(app/assets/examples/): TSP -> optimized_stops/route_line/distance_meters/
time_to_solve_ms; VRP -> id/origin/routes[].route_line/clients/.../time_to_solve_ms.

Como rodar (na raiz do opt-worker-solver, com o venv do worker ativo):
    python scripts/generate_examples.py            # gera tudo
    python scripts/generate_examples.py --tsp      # só TSP
    python scripts/generate_examples.py --vrp      # só VRP
    python scripts/generate_examples.py --out /tmp/exemplos   # outro destino

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
OUTPUT_DIR = Path(
    "/home/vinicius/Documents/projetos/opt-vgandolfi-dev/"
    "opt-vgandolfi-dev-web/app/assets/examples"
)

# Tempo máximo (s) que o VRP solver usa por exemplo. OR-Tools usa o time limit
# inteiro mesmo em instâncias pequenas; reduza para gerar mais rápido.
SOLVE_VRP_TIMEOUT_SECONDS = 30

# Solver TSP: True usa OSRM (matrix STREET); False usa haversine (EUCLIDIAN).
# Sem rede/OSRM, mantenha False (funciona offline).
TSP_USE_STREET = False

# ---------------------------------------------------------------------------

# Pontos turísticos reais de São Paulo (nome, lat, lng).
SP_LANDMARKS: list[tuple[str, float, float]] = [
    ("MASP", -23.5614, -46.6559),
    ("Parque Ibirapuera", -23.5874, -46.6576),
    ("Mercado Municipal", -23.5413, -46.6294),
    ("Pinacoteca", -23.5342, -46.6338),
    ("Museu do Ipiranga", -23.5852, -46.6097),
    ("Allianz Parque", -23.5272, -46.6785),
    ("Pico do Jaraguá", -23.4623, -46.7749),
    ("Catedral da Sé", -23.5506, -46.6338),
    ("Museu do Futebol", -23.5488, -46.6665),
    ("Parque Villa-Lobos", -23.5411, -46.7332),
    ("Zoológico", -23.6509, -46.6190),
    ("Horto Florestal", -23.4622, -46.6321),
    ("Shopping Eldorado", -23.5932, -46.6806),
    ("Parque do Carmo", -23.5729, -46.4878),
    ("Cantareira", -23.4615, -46.6319),
    ("Autódromo Interlagos", -23.7043, -46.6956),
    ("USP", -23.5614, -46.7308),
    ("Moema", -23.6027, -46.6604),
    ("Tatuapé", -23.5340, -46.5760),
    ("Santana", -23.5035, -46.6270),
    ("Museu Catavento", -23.5453, -46.6292),
    ("Teatro Municipal", -23.5453, -46.6387),
    ("Pacaembu", -23.5488, -46.6656),
    ("Parque da Água Branca", -23.5249, -46.6785),
    ("Liberdade", -23.5584, -46.6340),
    ("Bixiga", -23.5666, -46.6466),
    ("Luz", -23.5340, -46.6350),
    ("República", -23.5432, -46.6440),
    ("Anhangabaú", -23.5453, -46.6415),
    ("Centro Cultural SP", -23.5489, -46.6419),
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
        name, lat, lng = SP_LANDMARKS[i % len(SP_LANDMARKS)]
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


def resolve_vrp(n_clients: int, n_vehicles: int) -> dict:
    """Resolve um VRP com n_clients/n_vehicles e devolve dict pronto p/ JSON."""
    from app.algorithms.vrp.vrp_solver import VrpSolver
    from app.config import Settings
    from app.dtos import Address, MatrixType, VehicleType, VrpIn

    # Clientes espalhados nos landmarks
    clients = []
    for i in range(n_clients):
        name, lat, lng = SP_LANDMARKS[i % len(SP_LANDMARKS)]
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