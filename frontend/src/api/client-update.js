import axios from 'axios'

export const clientUpdateApi = {
  listDownloads: async () => {
    const res = await axios.get('/api/v1/client-updates/downloads')
    return res.data
  }
}
