#!/bin/sh
set -eu

STATE_FILE=/var/lib/firstboot-grow-rootfs.done
ATTEMPT_FILE=/var/lib/firstboot-grow-rootfs.attempts
MAX_ATTEMPTS=2
SERVICE_NAME=firstboot-grow-rootfs.service

log() {
  echo "[firstboot-grow-rootfs] $*"
}

root_src=$(findmnt -n -o SOURCE /)
root_dev=$(readlink -f "$root_src")
root_fs=$(findmnt -n -o FSTYPE /)

case "$root_dev" in
  /dev/*p[0-9]*)
    disk_dev="${root_dev%p[0-9]*}"
    part_num="${root_dev##*p}"
    ;;
  /dev/*[0-9])
    disk_dev="${root_dev%[0-9]*}"
    part_num="${root_dev##*[!0-9]}"
    ;;
  *)
    log "Unsupported root device format: $root_dev"
    exit 1
    ;;
esac

disk_name=$(basename "$disk_dev")
part_name=$(basename "$root_dev")

pttype=$(lsblk -dn -o PTTYPE "$disk_dev" 2>/dev/null | head -n1 | tr -d '\r')
log "root_dev=$root_dev disk_dev=$disk_dev part=$part_num fs=$root_fs pttype=${pttype:-unknown}"

case "$pttype" in
  gpt)
    log "Using systemd-repart for GPT disk"
    systemd-repart "$disk_dev"
    udevadm settle || true
    ;;
  dos|mbr|msdos|"")
    log "Using sfdisk fallback for MBR/DOS disk"

    disk_sectors=$(cat "/sys/class/block/$disk_name/size")
    part_sectors_before=$(cat "/sys/class/block/$part_name/size")

    start_sector=$(sfdisk -d "$disk_dev" | sed -n "s#^$root_dev[[:space:]]*:[[:space:]]*start=[[:space:]]*\([0-9][0-9]*\),.*#\1#p" | head -n1)

    if [ -z "$start_sector" ]; then
      log "Could not determine partition start for $root_dev"
      exit 1
    fi

    end_before=$((start_sector + part_sectors_before - 1))
    max_end=$((disk_sectors - 1))

    if [ "$end_before" -lt "$max_end" ]; then
      log "Growing partition $root_dev to end of disk"
      printf ', +\n' | sfdisk --force --no-reread -N "$part_num" "$disk_dev"
      blockdev --rereadpt "$disk_dev" || true
      udevadm settle || true
    else
      log "Partition already occupies full disk"
    fi

    part_sectors_after=$(cat "/sys/class/block/$part_name/size")
    end_after=$((start_sector + part_sectors_after - 1))

    if [ "$end_after" -lt "$max_end" ]; then
      attempts=$(cat "$ATTEMPT_FILE" 2>/dev/null || echo 0)
      if [ "$attempts" -ge "$MAX_ATTEMPTS" ]; then
        log "Kernel still sees old partition size after $attempts reboot(s); giving up"
        systemctl disable "$SERVICE_NAME" || true
        exit 1
      fi
      attempts=$((attempts + 1))
      echo "$attempts" > "$ATTEMPT_FILE"
      log "Kernel still sees old partition size; reboot required to continue (attempt $attempts/$MAX_ATTEMPTS)"
      systemctl --no-block reboot || true
      exit 0
    fi
    ;;
  *)
    log "Unsupported partition table type: $pttype"
    exit 1
    ;;
esac

case "$root_fs" in
  ext2|ext3|ext4)
    log "Resizing ext filesystem on $root_dev"
    resize2fs "$root_dev"
    ;;
  xfs)
    log "Resizing xfs filesystem on /"
    xfs_growfs /
    ;;
  btrfs)
    log "Resizing btrfs filesystem on /"
    btrfs filesystem resize max /
    ;;
  *)
    log "Unsupported filesystem type: $root_fs"
    exit 1
    ;;
esac

touch "$STATE_FILE"
rm -f "$ATTEMPT_FILE"
systemctl disable "$SERVICE_NAME" || true
log "Resize complete; marked done"
