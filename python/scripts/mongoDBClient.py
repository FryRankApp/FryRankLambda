from pymongo import MongoClient
import logging

class MongoDBClient:
    def __init__(self, connection_string, database_name):
        """
        Initialize the MongoDB client with connection parameters.

        Args:
            connection_string (str): MongoDB connection string URI
            database_name (str): Name of the database to connect to
        """
        self.connection_string = connection_string
        self.database_name = database_name
        self.client = None
        self.db = None
        self.connect_timeout = 10000
        self.read_timeout = 10000
        self.logger = logging.getLogger(__name__)

        # Connect on initialization
        self._connect()

    def _connect(self):
        try:
            # Configure client with explicit settings
            self.client = MongoClient(
                self.connection_string,
                connectTimeoutMS=self.connect_timeout,
                socketTimeoutMS=self.read_timeout
            )
            self.db = self.client[self.database_name]
            self.logger.info(f"Successfully connected to MongoDB database: {self.database_name}")
        except Exception as e:
            self.logger.error(f"Failed to connect to MongoDB: {str(e)}")
            raise

    def fetch_data(self, collection_name):
        """
        Fetch data from a MongoDB collection with optional filtering.

        Args:
            collection_name (str): Name of the collection to query

        Returns:
            list: List of documents matching the query
        """

        try:
            collection = self.db[collection_name]
            cursor = collection.find({}, None)

            return list(cursor)
        except Exception as e:
            self.logger.error(f"Error fetching data from collection {collection_name}: {str(e)}")
            raise

    def close(self):
        if self.client:
            self.client.close()
            self.logger.info("MongoDB connection closed")
            self.client = None
            self.db = None

    def __del__(self):
        self.close()
