package dev.vgandolfi.opt.orchestrator.infrastructure.s3;

/**
 * Porta de armazenamento de objetos (MinIO/S3). Interface fina para permitir
 * mock fácil nos testes unitários.
 */
public interface S3Storage {

    /**
     * Faz upload de um conteúdo JSON.
     *
     * @param key     chave do objeto (ex.: inputs/{jobId}.json)
     * @param content conteúdo JSON (string)
     * @return a própria key gravada
     */
    String uploadJson(String key, String content);

    /**
     * Baixa o conteúdo de um objeto JSON.
     *
     * @param key chave do objeto
     * @return conteúdo JSON como string
     */
    String downloadJson(String key);
}