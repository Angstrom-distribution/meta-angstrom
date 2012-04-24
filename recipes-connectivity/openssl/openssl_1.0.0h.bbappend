# This used to have an override, but that was fixed upstream.
# Keep bbappends to stop PR from going backward
PRINC := "${@int(PRINC) + 2}"

