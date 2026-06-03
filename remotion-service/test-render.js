const fs = require('fs');

async function triggerRender() {
  const scriptContent = fs.readFileSync('./test-script.json', 'utf8');
  const payload = {
    taskId: 'test_render_001',
    compositionScript: JSON.parse(scriptContent)
  };

  try {
    const res = await fetch('http://localhost:3001/render', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();
    console.log('Render trigger response:', data);

    // Poll for status
    const interval = setInterval(async () => {
      const statusRes = await fetch(`http://localhost:3001/render/${data.taskId}/status`);
      const statusData = await statusRes.json();
      console.log(`[${statusData.status}] Progress: ${(statusData.progress * 100).toFixed(1)}%`);
      
      if (statusData.status === 'DONE' || statusData.status === 'FAILED') {
        clearInterval(interval);
        console.log('Final Result:', statusData);
      }
    }, 2000);

  } catch (err) {
    console.error('Failed to trigger render:', err);
  }
}

triggerRender();
