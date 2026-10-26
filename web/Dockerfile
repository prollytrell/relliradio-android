# Caddy web server with the DuckDNS plugin added, so it can get HTTPS certificates
# by writing a DNS record instead of opening a port to the internet.
FROM caddy:2-builder AS builder
RUN xcaddy build --with github.com/caddy-dns/duckdns

FROM caddy:2
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
