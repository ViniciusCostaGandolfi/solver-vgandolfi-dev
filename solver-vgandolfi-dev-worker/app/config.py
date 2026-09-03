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
    LKH_MAX_ITERATIONS: int = 30000
    ALNS_ITERATIONS: int = 1500

    MAX_CONCURRENT_TSP: int = 1
    MAX_CONCURRENT_VRP: int = 2
    MAX_CONCURRENT_MATRIX: int = 2

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
