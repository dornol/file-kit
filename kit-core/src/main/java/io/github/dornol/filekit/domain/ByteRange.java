package io.github.dornol.filekit.domain;

import io.github.dornol.filekit.storage.FileStorageException;

/**
 * Represents a byte range for HTTP Range requests.
 *
 * @param start     start byte offset (inclusive)
 * @param end       end byte offset (inclusive)
 * @param totalSize total file size in bytes
 */
public record ByteRange(long start, long end, long totalSize) {

    public ByteRange {
        if (totalSize <= 0) {
            throw new IllegalArgumentException("totalSize must be positive: " + totalSize);
        }
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end must be >= start: start=" + start + ", end=" + end);
        }
        if (end >= totalSize) {
            throw new IllegalArgumentException("end must be < totalSize: end=" + end + ", totalSize=" + totalSize);
        }
    }

    /**
     * Returns the number of bytes in this range.
     */
    public long length() {
        return end - start + 1;
    }

    /**
     * Returns the Content-Range header value (e.g. "bytes 0-499/1000").
     */
    public String toContentRangeHeader() {
        return "bytes " + start + "-" + end + "/" + totalSize;
    }

    /**
     * Parses an HTTP Range header value (e.g. "bytes=0-499") for the given total size.
     * Only single byte ranges are supported.
     *
     * @param rangeHeader the Range header value
     * @param totalSize   total file size in bytes
     * @return parsed byte range
     * @throws FileStorageException if the range is invalid or unsatisfiable
     */
    public static ByteRange parse(String rangeHeader, long totalSize) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            throw new FileStorageException(FileStorageException.RANGE_NOT_SATISFIABLE,
                    "Invalid Range header: " + rangeHeader);
        }

        String rangeSpec = rangeHeader.substring(6).trim();

        // Check for multi-range (not supported)
        if (rangeSpec.contains(",")) {
            throw new FileStorageException(FileStorageException.RANGE_NOT_SATISFIABLE,
                    "Multi-range requests are not supported");
        }

        try {
            long start;
            long end;

            if (rangeSpec.startsWith("-")) {
                // Suffix range: -500 means last 500 bytes
                long suffix = Long.parseLong(rangeSpec.substring(1));
                if (suffix <= 0) {
                    throw new FileStorageException(FileStorageException.RANGE_NOT_SATISFIABLE,
                            "Suffix length must be positive: " + rangeHeader);
                }
                start = Math.max(0, totalSize - suffix);
                end = totalSize - 1;
            } else if (rangeSpec.endsWith("-")) {
                // Open-ended: 500- means from 500 to end
                start = Long.parseLong(rangeSpec.substring(0, rangeSpec.length() - 1));
                end = totalSize - 1;
            } else {
                String[] parts = rangeSpec.split("-", 2);
                start = Long.parseLong(parts[0]);
                end = Long.parseLong(parts[1]);
            }

            if (start >= totalSize) {
                throw new FileStorageException(FileStorageException.RANGE_NOT_SATISFIABLE,
                        "Range start (" + start + ") >= total size (" + totalSize + ")");
            }

            // Clamp end to totalSize - 1
            end = Math.min(end, totalSize - 1);

            return new ByteRange(start, end, totalSize);
        } catch (NumberFormatException e) {
            throw new FileStorageException(FileStorageException.RANGE_NOT_SATISFIABLE,
                    "Invalid Range header format: " + rangeHeader, e);
        }
    }
}
