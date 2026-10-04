.PHONY: bump

# Usage: make bump VERSION=1.10.1
bump:
	@scripts/bump-version.sh $(VERSION)
