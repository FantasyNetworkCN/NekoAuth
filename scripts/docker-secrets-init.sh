#!/bin/sh
set -eu

SECRETS_DIR=${SECRETS_DIR:-/run/secrets}
SOURCE_DIR=${SOURCE_DIR:-/bootstrap-config/secrets}

umask 077
mkdir -p "$SECRETS_DIR"

copy_override() {
  name="$1"
  source="$SOURCE_DIR/$name"
  target="$SECRETS_DIR/$name"
  if [ -s "$source" ]; then
    rm -f "$target"
    cp "$source" "$target"
    chmod 0444 "$target"
    echo "Loaded $name from $SOURCE_DIR"
  fi
}

generate_secret() {
  name="$1"
  bytes="$2"
  target="$SECRETS_DIR/$name"
  if [ ! -s "$target" ]; then
    head -c "$bytes" /dev/urandom | base64 | tr -d '\n' > "$target"
    chmod 0444 "$target"
    echo "Generated $name"
  fi
}

copy_override db_password
copy_override jwt_secret
copy_override mysql_root_password
copy_override admin_password

generate_secret db_password 48
generate_secret jwt_secret 48
generate_secret mysql_root_password 48

if [ ! -s "$SECRETS_DIR/admin_password" ]; then
  if [ -n "${NEKO_ADMIN_PASSWORD:-}" ]; then
    printf '%s' "$NEKO_ADMIN_PASSWORD" > "$SECRETS_DIR/admin_password"
  else
    head -c 18 /dev/urandom | base64 | tr -d '\n' > "$SECRETS_DIR/admin_password"
  fi
  chmod 0444 "$SECRETS_DIR/admin_password"
  echo "Generated admin_password"
fi

echo "NekoAuth secrets are ready"
