import json
import logging
from typing import Any, Dict

import boto3
from botocore.config import Config

from app.config import settings

logger = logging.getLogger(__name__)


class S3Service:
    def __init__(self):
        self.client = boto3.client(
            "s3",
            endpoint_url=settings.S3_ENDPOINT,
            aws_access_key_id=settings.S3_ACCESS_KEY,
            aws_secret_access_key=settings.S3_SECRET_KEY,
            region_name=settings.S3_REGION,
            config=Config(s3={"addressing_style": "path"}),
        )
        self.bucket = settings.S3_BUCKET_NAME

    def check_connection(self):
        try:
            buckets = self.client.list_buckets()
            logger.info(f"S3 connected. Available buckets: {[b['Name'] for b in buckets.get('Buckets', [])]}")
        except Exception as e:
            logger.warning(f"S3 connection check failed: {e}")

    def upload_json(self, data: Dict[str, Any], prefix: str = "solutions") -> str:
        import uuid
        key = f"{prefix}/{uuid.uuid4()}.json"
        self.client.put_object(Bucket=self.bucket, Key=key, Body=json.dumps(data, default=str))
        logger.info(f"Uploaded to S3: {key}")
        return key

    def download_json(self, key: str) -> Dict[str, Any]:
        response = self.client.get_object(Bucket=self.bucket, Key=key)
        return json.loads(response["Body"].read().decode("utf-8"))
