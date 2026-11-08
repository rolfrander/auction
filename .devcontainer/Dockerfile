# Dev container: JDK 21 + Clojure CLI (same base as the production build stage),
# plus jq and the Scaleway CLI for deploy/*.sh. Node and Docker come from
# features in devcontainer.json.
FROM clojure:temurin-21-tools-deps-bookworm

RUN apt-get update \
 && apt-get install -y --no-install-recommends jq rlwrap curl ca-certificates git \
 && rm -rf /var/lib/apt/lists/*

ARG SCW_VERSION=2.65.2
ARG SCW_SHA256_AMD64=184e697e6ab8a8e825be1c5cf61d104341abe3598bb0e20b7a54618cb05231b3
ARG SCW_SHA256_ARM64=28f2115b7fce9d23f5726dc38566c7c973f579804be78a46f908c1a57da71893
RUN set -eux; \
    arch=$(dpkg --print-architecture); \
    case "$arch" in \
      amd64) sum=$SCW_SHA256_AMD64 ;; \
      arm64) sum=$SCW_SHA256_ARM64 ;; \
      *) echo "unsupported arch $arch" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o /usr/local/bin/scw \
      "https://github.com/scaleway/scaleway-cli/releases/download/v${SCW_VERSION}/scaleway-cli_${SCW_VERSION}_linux_${arch}"; \
    echo "$sum  /usr/local/bin/scw" | sha256sum -c -; \
    chmod +x /usr/local/bin/scw
