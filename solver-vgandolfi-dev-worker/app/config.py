from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    MQ_HOST: str = "localhost"
    MQ_PORT: int = 5672
    MQ_USER: str = "solver-vgandolfi-dev"
    MQ_PASSWORD: str = "solver-vgandolfi-dev"

    S3_ENDPOINT: str = "http://localhost:9000"
    S3_ACCESS_KEY: str = "minioadmin"
    S3_SECRET_KEY: str = "minioadmin"
    S3_BUCKET_NAME: str = "solver-vgandolfi-dev"
    S3_REGION: str = "garage"

    OSRM_URL: str = "https://osrm.rotaslivres.com.br"
    OSRM_VERIFY_SSL: bool = False

    VRP_TIMEOUT_SECONDS: int = 600
    # Orçamento TOTAL (segundos) de processamento por problema. Nenhum solver
    # pode ultrapassar esse teto — a busca para com a melhor solução até ali.
    SOLVER_TIME_BUDGET_SECONDS: int = 20
    # Limite individual da busca do OR-Tools CVRP (nunca ultrapassa o orçamento
    # total; a solução converge rápido e tempo extra só gasta CPU sem qualidade:
    # medido 10s ≈ 120s para 50 clientes).
    ORTOOLS_TIME_LIMIT_SECONDS: int = 10
    LKH_MAX_ITERATIONS: int = 30000
    ALNS_ITERATIONS: int = 1500

    MAX_CONCURRENT_TSP: int = 1
    MAX_CONCURRENT_VRP: int = 2
    MAX_CONCURRENT_MATRIX: int = 2

    # Threads used to fetch STREET path polylines in parallel
    # (one OSRM Route request per pair of points).
    MATRIX_PATH_WORKERS: int = 8

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
