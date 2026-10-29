const express = require('express');
const { Pool } = require('pg');
const app = express();
app.use(express.json());

const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

// Senkronizasyon (Basit aile senkronizasyonu)
app.post('/api/sync', async (req, res) => {
  const { profileId, data } = req.body;
  // Burada verileri PostgreSQL'e yazma mantığı olacak
  res.status(200).send('Senkronize edildi.');
});

// Profil yönetimi
app.get('/api/profiles/:familyId', async (req, res) => {
    const { familyId } = req.params;
    const result = await pool.query('SELECT * FROM profiles WHERE family_id = $1', [familyId]);
    res.json(result.rows);
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Backend ${PORT} portunda çalışıyor.`));
