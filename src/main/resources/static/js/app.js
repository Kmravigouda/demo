const form = document.getElementById("uploadForm");
const fileInput = document.getElementById("fileInput");
const submitButton = document.getElementById("submitButton");
const progressSection = document.getElementById("progressSection");
const progressText = document.getElementById("progressText");
const progressPercent = document.getElementById("progressPercent");
const progressBar = document.getElementById("progressBar");
const resultCard = document.getElementById("resultCard");
const resultMessage = document.getElementById("resultMessage");
const downloadLink = document.getElementById("downloadLink");
const errorCard = document.getElementById("errorCard");

let progressTimer;

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    if (!fileInput.files.length) {
        showError("Please choose a file before generating the report.");
        return;
    }

    resetState();
    submitButton.disabled = true;
    progressSection.classList.remove("hidden");
    animateProgress();

    try {
        const formData = new FormData();
        formData.append("file", fileInput.files[0]);

        const response = await fetch("/api/reports", {
            method: "POST",
            body: formData
        });

        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.message || "Unable to generate report.");
        }

        setProgress(100, "Report ready for download");
        clearInterval(progressTimer);
        resultMessage.textContent = `${payload.message} ${payload.processedStudents} student record(s) processed.`;
        downloadLink.href = payload.downloadUrl;
        resultCard.classList.remove("hidden");
    } catch (error) {
        clearInterval(progressTimer);
        setProgress(100, "Processing stopped");
        showError(error.message);
    } finally {
        submitButton.disabled = false;
    }
});

function animateProgress() {
    const phases = [
        { percent: 18, text: "Uploading student file..." },
        { percent: 42, text: "Reading student records..." },
        { percent: 68, text: "Fetching LeetCode stats..." },
        { percent: 88, text: "Preparing download..." }
    ];

    let index = 0;
    setProgress(8, "Preparing upload...");
    progressTimer = setInterval(() => {
        if (index >= phases.length) {
            clearInterval(progressTimer);
            return;
        }
        const phase = phases[index++];
        setProgress(phase.percent, phase.text);
    }, 900);
}

function setProgress(value, text) {
    progressBar.style.width = `${value}%`;
    progressPercent.textContent = `${value}%`;
    progressText.textContent = text;
}

function resetState() {
    clearInterval(progressTimer);
    setProgress(0, "Preparing upload...");
    resultCard.classList.add("hidden");
    errorCard.classList.add("hidden");
    errorCard.textContent = "";
}

function showError(message) {
    errorCard.textContent = message;
    errorCard.classList.remove("hidden");
}
