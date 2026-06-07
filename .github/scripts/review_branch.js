const { execSync } = require('child_process');

// 1. Check API Key
const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  console.log("Warning: GEMINI_API_KEY is not set. Skipping automated code review.");
  process.exit(process.env.GITHUB_ACTIONS ? 1 : 0);
}

try {
  // 2. Fetch git diff of current branch compared to base branch
  const baseBranch = process.env.GITHUB_BASE_REF || 'main';
  console.log(`Comparing against base branch: ${baseBranch}`);
  let diff = "";
  try {
    diff = execSync(`git diff origin/${baseBranch}`).toString();
  } catch (err) {
    diff = execSync(`git diff ${baseBranch}`).toString();
  }
  if (!diff.trim()) {
    console.log("No changes to review.");
    process.exit(0);
  }

  // 3. Run local Maven test suite to get build/test results
  console.log("Running local test suite...");
  let testResults = "";
  try {
    testResults = execSync('mvn clean test').toString();
  } catch (err) {
    testResults = err.stdout?.toString() || err.stderr?.toString() || err.message;
  }

  // 4. Construct Gemini Prompt
  const systemInstruction = 
    "You are an expert Java/Maven code reviewer. Review the provided git diff and " +
    "corresponding test execution log. Identify:\n" +
    "1. Nullability/Validation checks (e.g. check for negative and null values in config builders).\n" +
    "2. Boundary/Edge cases or performance issues (e.g. infinite or NaN double values, thread-blocking sleeps).\n" +
    "3. Incorrect exception handling (make sure PolityException is thrown, not LlmException).\n" +
    "Be concise, constructive, and output markdown. At the very end of your review, you MUST " +
    "output either 'DECISION: BLOCK' (if there are critical issues blocking the PR) or " +
    "'DECISION: PASS' (if the changes are approved to merge).";

  const prompt = `Here is the code diff:\n\n\`\`\`diff\n${diff}\n\`\`\`\n\n` +
                 `Here are the maven test results:\n\n\`\`\`text\n${testResults}\n\`\`\`\n\n` +
                 `Please review the code and test run output.`;

  // 5. Call Gemini API using built-in fetch
  console.log("Consulting the reviewer agent...");
  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;
  
  const body = {
    contents: [{ parts: [{ text: prompt }] }],
    systemInstruction: { parts: [{ text: systemInstruction }] }
  };

  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  .then(res => res.json())
  .then(data => {
    const report = data.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!report) {
      console.error("Error: Failed to get response from Gemini API. Response received:", JSON.stringify(data));
      process.exit(1);
    }

    console.log("\n=== AGENT CODE REVIEW REPORT ===\n");
    console.log(report);

    if (report.includes("DECISION: BLOCK")) {
      console.log("\n[BLOCKED] Agent flagged critical issues in the code.");
      process.exit(1);
    } else {
      console.log("\n[PASSED] Agent approved the changes.");
      process.exit(0);
    }
  })
  .catch(err => {
    console.error("Error calling Gemini API:", err);
    process.exit(1);
  });

} catch (err) {
  console.error("Error executing review agent:", err);
  process.exit(1);
}
