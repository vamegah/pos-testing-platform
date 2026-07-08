# simulators/product-profiles/tcx-display/simulator.py

"""
TCx® Display Simulator

Display capability matrix (size range, horizontal/vertical orientation) as config,
with a mock render-target that validates the requested resolution/orientation combo
is in the supported matrix.

Supports:
  - Display size validation (10.1, 12.1, 15.6, 21.5 inches)
  - Orientation validation (landscape/portrait)
  - Resolution validation
  - Size-orientation matrix validation

All data is mocked — no real display hardware is involved.
"""

import os
import json
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime
from flask import Flask, request, jsonify, abort

# Configure logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Display capability matrix
SUPPORTED_SIZES = [10.1, 12.1, 15.6, 21.5]
SUPPORTED_RESOLUTIONS = [
    {"width": 1280, "height": 800, "orientation": "landscape"},
    {"width": 800, "height": 1280, "orientation": "portrait"},
    {"width": 1920, "height": 1080, "orientation": "landscape"},
    {"width": 1080, "height": 1920, "orientation": "portrait"},
]
SUPPORTED_ORIENTATIONS = ["landscape", "portrait"]

# Size-orientation matrix (which orientations are supported for each size)
SIZE_ORIENTATION_MATRIX = {
    "10.1": ["landscape", "portrait"],
    "12.1": ["landscape", "portrait"],
    "15.6": ["landscape", "portrait"],
    "21.5": ["landscape"],
}

# Display state (simulated)
display_state = {
    "current_size": 15.6,
    "current_orientation": "landscape",
    "current_resolution": {"width": 1920, "height": 1080},
}

# Render validation history
validation_history = []


@app.route("/health", methods=["GET"])
def health():
    """Health check endpoint."""
    return jsonify({"status": "healthy", "service": "tcx-display-simulator"}), 200


@app.route("/tcx-display/capabilities", methods=["GET"])
def get_capabilities():
    """Get the display capability matrix."""
    return (
        jsonify(
            {
                "supported_sizes": SUPPORTED_SIZES,
                "supported_resolutions": SUPPORTED_RESOLUTIONS,
                "supported_orientations": SUPPORTED_ORIENTATIONS,
                "size_orientation_matrix": SIZE_ORIENTATION_MATRIX,
            }
        ),
        200,
    )


@app.route("/tcx-display/state", methods=["GET"])
def get_display_state():
    """Get the current display state."""
    return (
        jsonify(
            {
                "current_size": display_state["current_size"],
                "current_orientation": display_state["current_orientation"],
                "current_resolution": display_state["current_resolution"],
            }
        ),
        200,
    )


@app.route("/tcx-display/render/validate", methods=["POST"])
def validate_render():
    """
    Validate that a requested display configuration is supported.

    Expected payload:
    {
        "size_inches": 15.6,
        "orientation": "landscape",
        "resolution": {"width": 1920, "height": 1080}
    }
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    size = data.get("size_inches")
    orientation = data.get("orientation")
    resolution = data.get("resolution", {})

    if size is None:
        abort(400, description="Missing 'size_inches' field")
    if not orientation:
        abort(400, description="Missing 'orientation' field")
    if not resolution or "width" not in resolution or "height" not in resolution:
        abort(400, description="Missing 'resolution' or resolution fields")

    # Validate size
    size_str = str(size)
    if size not in SUPPORTED_SIZES:
        return (
            jsonify(
                {
                    "valid": False,
                    "reason": f"Size {size} not supported. Supported: {SUPPORTED_SIZES}",
                    "size": size,
                    "orientation": orientation,
                    "resolution": resolution,
                }
            ),
            400,
        )

    # Validate orientation
    if orientation not in SUPPORTED_ORIENTATIONS:
        return (
            jsonify(
                {
                    "valid": False,
                    "reason": f"Orientation '{orientation}' not supported. Supported: {SUPPORTED_ORIENTATIONS}",
                    "size": size,
                    "orientation": orientation,
                    "resolution": resolution,
                }
            ),
            400,
        )

    # Validate size-orientation combo
    if orientation not in SIZE_ORIENTATION_MATRIX.get(size_str, []):
        return (
            jsonify(
                {
                    "valid": False,
                    "reason": f"Orientation '{orientation}' not supported for size {size}. Supported: {SIZE_ORIENTATION_MATRIX.get(size_str, [])}",
                    "size": size,
                    "orientation": orientation,
                    "resolution": resolution,
                }
            ),
            400,
        )

    # Validate resolution
    resolution_valid = False
    for res in SUPPORTED_RESOLUTIONS:
        if (
            res["width"] == resolution["width"]
            and res["height"] == resolution["height"]
        ):
            if res["orientation"] == orientation:
                resolution_valid = True
                break

    if not resolution_valid:
        return (
            jsonify(
                {
                    "valid": False,
                    "reason": f"Resolution {resolution['width']}x{resolution['height']} not supported for orientation '{orientation}'. Supported: {[r for r in SUPPORTED_RESOLUTIONS if r['orientation'] == orientation]}",
                    "size": size,
                    "orientation": orientation,
                    "resolution": resolution,
                }
            ),
            400,
        )

    # Log validation
    validation_entry = {
        "size": size,
        "orientation": orientation,
        "resolution": resolution,
        "valid": True,
        "timestamp": datetime.utcnow().isoformat() + "Z",
    }
    validation_history.append(validation_entry)

    # Update display state
    display_state["current_size"] = size
    display_state["current_orientation"] = orientation
    display_state["current_resolution"] = resolution

    logger.info(
        f"Display validated: {size}\" {orientation} {resolution['width']}x{resolution['height']}"
    )

    return (
        jsonify(
            {
                "valid": True,
                "size": size,
                "orientation": orientation,
                "resolution": resolution,
                "timestamp": validation_entry["timestamp"],
                "message": f"Display configuration validated: {size}\" {orientation} {resolution['width']}x{resolution['height']}",
            }
        ),
        200,
    )


@app.route("/tcx-display/validation/history", methods=["GET"])
def get_validation_history():
    """Get the validation history."""
    return (
        jsonify({"history": validation_history, "count": len(validation_history)}),
        200,
    )


@app.route("/tcx-display/transaction", methods=["POST"])
def process_transaction():
    """
    Process a transaction with the current display configuration.
    The display config is validated before processing.
    """
    data = request.get_json()
    if not data:
        abort(400, description="Request body is required")

    items = data.get("items", [])
    region = data.get("region", "CA")
    payment = data.get("payment", {})
    size = data.get("size_inches", display_state["current_size"])
    orientation = data.get("orientation", display_state["current_orientation"])
    resolution = data.get("resolution", display_state["current_resolution"])

    if not items:
        abort(400, description="Missing 'items' field")

    # Validate display configuration first
    validate_payload = {
        "size_inches": size,
        "orientation": orientation,
        "resolution": resolution,
    }

    # Use the validate endpoint internally
    with app.test_request_context(
        "/tcx-display/render/validate", method="POST", json=validate_payload
    ):
        response = validate_render()
        if response.status_code != 200:
            return (
                jsonify(
                    {
                        "status": "failed",
                        "step": "display_validation",
                        "error": response.get_json(),
                    }
                ),
                400,
            )

    # Update display state
    display_state["current_size"] = size
    display_state["current_orientation"] = orientation
    display_state["current_resolution"] = resolution

    # Transaction would continue here, but for this profile we just validate the display
    # The actual transaction processing is handled by the other services

    return (
        jsonify(
            {
                "status": "display_validated",
                "display_config": {
                    "size_inches": size,
                    "orientation": orientation,
                    "resolution": resolution,
                },
                "message": f"Display configuration validated successfully: {size}\" {orientation} {resolution['width']}x{resolution['height']}",
            }
        ),
        200,
    )


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5006))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
