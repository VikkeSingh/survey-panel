// Create Vendor Page JavaScript

const token = localStorage.getItem("jwtToken");
if (!token) {
    window.location.href = "/login"; // Redirect if not logged in
} else {

    document.addEventListener("DOMContentLoaded", function () {

        const urlFieldIds = [
            "completed",
            "terminated",
            "quota",
            "securityTerminate"
        ];

        // Comes from app.company-identifier, rendered into <meta name="company-identifier">
        const companyIdentifier = (document.querySelector('meta[name="company-identifier"]') || {}).content || "";
        const requiredUrlToken = companyIdentifier ? "=[" + companyIdentifier + "]" : "";

        if (!requiredUrlToken) {
            console.warn("company-identifier is not configured; skipping the =[IDENTIFIER] URL check.");
        }

        function isValidHttpsUrl(value) {
            return (
                value &&
                value.startsWith("https://") &&
                (!requiredUrlToken || value.includes(requiredUrlToken))
            );
        }

        // Live validation while typing
        urlFieldIds.forEach(id => {
            const input = document.getElementById(id);
            input.addEventListener("input", () => {
                if (isValidHttpsUrl(input.value)) {
                    input.classList.remove("is-invalid");
                } else {
                    input.classList.add("is-invalid");
                }
            });
        });

        document.getElementById("addForm").addEventListener("submit", function (event) {
            event.preventDefault(); // Prevent default form submission

            let hasError = false;

            // Validate URLs before submit
            urlFieldIds.forEach(id => {
                const input = document.getElementById(id);
                if (!isValidHttpsUrl(input.value)) {
                    input.classList.add("is-invalid");
                    hasError = true;
                }
            });

            if (hasError) {
                alert("Please enter valid HTTPS URLs (must start with https:// and contain " + requiredUrlToken + ")");
                return; // ❌ Stop API call
            }

            // Show loader only if validation passes
            document.getElementById("loader").style.display = "block";

            // Collect form data
            const formData = {
                username: document.getElementById("username").value,
                email: document.getElementById("email").value,
                company: document.getElementById("company").value,
                complete: document.getElementById("completed").value,
                terminate: document.getElementById("terminated").value,
                quotafull: document.getElementById("quota").value,
                securityTerminate: document.getElementById("securityTerminate").value
            };

            // Call backend API
            fetch("/admin/vendors/create", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": "Bearer " + token
                },
                body: JSON.stringify(formData)
            })
            .then(response => {
                if (response.status === 401) {
                    alert("Session expired. Please log in again.");
                    localStorage.removeItem("jwtToken");
                    window.location.href = "/login";
                    return;
                }
                return response.json();
            })
            .then(data => {
                console.log("Success:", data);
                alert("Vendor added successfully!");
                document.getElementById("addForm").reset();

                // Remove validation styles after reset
                urlFieldIds.forEach(id => {
                    document.getElementById(id).classList.remove("is-invalid");
                });
            })
            .catch(error => {
                console.error("Error:", error);
                alert("Vendor already exists. Please try again with a different vendor username.");
            })
            .finally(() => {
                document.getElementById("loader").style.display = "none";
            });
        });
    });
}
